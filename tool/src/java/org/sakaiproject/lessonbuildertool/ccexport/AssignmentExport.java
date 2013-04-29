/**********************************************************************************
 * $URL: $
 * $Id: $
 ***********************************************************************************
 *
 * Author: Charles Hedrick, hedrick@rutgers.edu
 *
 * Copyright (c) 2013 Rutgers, the State University of New Jersey
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");                                                                
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.lessonbuildertool.ccexport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Iterator;
import java.net.URLEncoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.sakaiproject.component.cover.ServerConfigurationService;

import org.w3c.dom.Document;

import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.tool.cover.SessionManager;

import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.CacheRefresher;
import org.sakaiproject.memory.api.MemoryService;

import uk.org.ponder.messageutil.MessageLocator;

import org.sakaiproject.lessonbuildertool.SimplePageItem;
import org.sakaiproject.lessonbuildertool.model.SimplePageToolDao;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.db.api.SqlReader;
import java.sql.Connection;
import java.sql.ResultSet;
import org.sakaiproject.lessonbuildertool.ccexport.ZipPrintStream;
import org.sakaiproject.lessonbuildertool.service.LessonEntity;

import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.assignment.api.AssignmentEdit;
import org.sakaiproject.assignment.api.AssignmentSubmission;
import org.sakaiproject.assignment.api.AssignmentContent;
import org.sakaiproject.assignment.api.AssignmentContentEdit;
import org.sakaiproject.assignment.cover.AssignmentService;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;


/*
 * set up as a singleton, but also instantiated by CCExport.
 * The purpose of the singleton setup is just to get the dependencies.
 * So they are all declared static.
 */

public class AssignmentExport {

    private static Log log = LogFactory.getLog(AssignmentExport.class);

    private static SimplePageToolDao simplePageToolDao;

    public void setSimplePageToolDao(Object dao) {
	simplePageToolDao = (SimplePageToolDao) dao;
    }

    static MessageLocator messageLocator = null;
    public void setMessageLocator(MessageLocator m) {
	messageLocator = m;
    }

    CCExport ccExport = null;

    public void init () {
	// currently nothing to do

	log.info("init()");

    }

    public void destroy()
    {
	log.info("destroy()");
    }

    // find topics in site, but organized by forum
    public List<String> getEntitiesInSite(String siteId, CCExport bean) {

	List<String> ret = new ArrayList<String>();
	String siteRef = "/group/" + siteId + "/";

	// security. assume this is only used in places where it's OK, so skip security checks
	Iterator i = AssignmentService.getAssignmentsForContext(siteId);
	while (i.hasNext()) {
	    Assignment assignment = (Assignment)i.next();

	    String deleted = assignment.getProperties().getProperty(ResourceProperties.PROP_ASSIGNMENT_DELETED);
	    if ((deleted == null || "".equals(deleted)) && !assignment.getDraft()) {
		AssignmentContent content = assignment.getContent();
		List<Reference>attachments = content.getAttachments();

		String instructions = content.getInstructions();
		
		// special case. one attachment and nothing else.
		// just export the attachment
		String intendeduse = null;
		if ((instructions == null || instructions.trim().equals("")) &&
		    (attachments != null && attachments.size() == 1)) {
		    intendeduse = "assignment";  // simple case. just set intended use for attachment
		} else { // complex case. need to do full assignment generation
		    ret.add(LessonEntity.ASSIGNMENT + "/" + assignment.getId().toString());
		}

		for (Reference ref: attachments) {
		    String sakaiId = ref.getReference();
		    if (sakaiId.startsWith("/content/"))
			sakaiId = sakaiId.substring("/content".length());
		    
		    // if attachment isn't a file in resources, arrange for it to be included
		    if (! sakaiId.startsWith(siteRef)) {  // if in resources, already included
			int lastSlash = sakaiId.lastIndexOf("/");
			String lastAtom = sakaiId.substring(lastSlash + 1);
			bean.addFile(sakaiId, "attachments/" + assignment.getId() + "/" + lastAtom, intendeduse);
		    } else if (intendeduse != null) {  // already there, just set intended use
			bean.setIntendeduse(sakaiId, intendeduse);
		    }
		}
	    }
	}
	return ret;
    }

    // this is weird, because it's a web content, not a learning application. So we need to produce an HTML file
    // with instructions and relative references to any attachments

    public boolean outputEntity(String assignmentRef, ZipPrintStream out, PrintStream errStream, CCExport bean, CCExport.Resource resource) {

	int i = assignmentRef.indexOf("/");
	String assignmentId = assignmentRef.substring(i+1);
	ccExport = bean;

	Assignment assignment = null;

	try {
	    assignment = AssignmentService.getAssignment(assignmentId);
	} catch (Exception e) {
	    System.out.println("failed to find " + assignmentId);
	    return false;
	}

	String title = assignment.getTitle();
	String attachmentDir = "attachments/" + assignment.getId() + "/";

	AssignmentContent content = assignment.getContent();
	String instructions = content.getInstructions();
	List<Reference>attachments = content.getAttachments();

	out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
	out.println("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">");
	out.println("<body>");
	if (instructions != null && !instructions.trim().equals("")) {
	    out.println("<div>");
	    out.println(instructions);
	    out.println("</div>");
	}
	for (Reference ref: attachments) {
	    String sakaiId = ref.getReference();
	    if (sakaiId.startsWith("/content/"))
		sakaiId = sakaiId.substring("/content".length());
	    String location = bean.getLocation(sakaiId);
	    int lastSlash = sakaiId.lastIndexOf("/");
	    String lastAtom = sakaiId.substring(lastSlash + 1);
	    String URL = null;
	    if (location.startsWith(attachmentDir))
		URL = lastAtom;  // if in attachment dir, relative reference
	    else
		URL = "../../" + location;  // else it's in the normal site content
	    URL = URL.replaceAll("//", "/");
	    try {
		out.println("<a href=\"" + URLEncoder.encode(URL, "UTF-8") + "\">" + StringEscapeUtils.escapeHtml(lastAtom) + "</a><br/>");
	    } catch (java.io.UnsupportedEncodingException e) {
		System.out.println("UTF-8 unsupported");
	    }
	    bean.addDependency(resource, sakaiId);
	}
	out.println("</body>");
	out.println("</html>");

	return true;
   }

}











































































































































































































































































































































































