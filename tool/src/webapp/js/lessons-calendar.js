$(function(){
    var url = $('.site-events-url').text().replace(/'/g,"");
    var moreInfoUrl = $('.event-tool-url').text().replace(/'/g,"");
    
    $('.calendar-div').fullCalendar({
        displayEventTime: false,
        height: 400,
        header:
        {
            left: 'prev,next today',
            center: 'title',
            right: 'month,agendaWeek,agendaDay'
        },
        eventSources: [
            {
                events: function(start, end, timezone, callback) {
                    var start_date =  $('.calendar-div').fullCalendar('getView').start.format('YYYY-MM-DD');
                    var end_date  =   $('.calendar-div').fullCalendar('getView').end.format('YYYY-MM-DD');
                    $.ajax({
                        url: url+'.json',
                        dataType: 'json',
                        data:{ merged: "true", firstDate: start_date, lastDate:end_date},
                        cache: false,
                        success: function(data) {
                            var events = [];
                            $(data["calendar_collection"]).each(function() {
                                var startTime = this["firstTime"]["time"];
                                var startDate = new Date(startTime);
                                var endTime = this["firstTime"]["time"] + this["duration"] ;
                                var endDate = new Date();
                                endDate.setTime(endTime);
                                events.push({
                                    title: this["title"],
                                    description: this["description"],
                                    start: startDate,
                                    site_name: this["siteName"],
                                    type: this["type"],
                                    icon: this["eventImage"],
                                    event_id: this["eventId"],
                                    attachments: this["attachments"],
                                    eventReference: this["eventReference"],
                                    end: endDate
                                });
                            });
                            callback(events);
                        }
                    });
                },
                color: '#D4DFEE',
                textColor: '#0064cd'
            }
        ],
        eventClick: function(event, jsEvent, view){
            //to adjust startdate and enddate as per user's locale
            var startDate = new Date(event.start).toLocaleString();
            var endDate = new Date(event.end).toLocaleString();
            $("#startTime").text(moment(startDate, 'DD/MM/YYYY, HH:mm:ss').format('DD-MM-YYYY hh:mm A'));
            $("#endTime").text(moment(endDate, 'DD/MM/YYYY, hh:mm:').format('DD-MM-YYYY hh:mm A'));
            var src = "/library/image/" + event.icon;
            $('#event-type-icon').attr("src",src);
            $("#event-type").text(event.type);
            $("#event-description").html(event.description);
            $("#site-name").text(event.site_name);
            //if event has attachment show attachment info div , else hide it
            if(event.attachments.length >= 1){
                var attachments = "<ul class='eventAttachmentList'>";
                var altMessage = msg("simplepage.eventAttachments");
                for(i=0; i< event.attachments.length; i++){
                    var href = event.attachments[i].url;
                    var filename = href.split('/');
                    filename = filename[filename.length-1];
                    attachments += '<li class="eventAttachmentItem"><a href="'+href+'" target="_blank">'+filename+'</a><img src="/library/image/sakai/attachments.gif" alt="' + altMessage + '"></li>';
                }
                attachments += "</ul>"
                $('#event-attachments').html(attachments);
            }
            else{
                $("#eventAttachmentsDiv").hide();
            }
            var more_info = moreInfoUrl + event.eventReference + "&panel=Main&sakai_action=doDescription&sakai.state.reset=true";
            var fullDetailsText = msg("simplepage.calendar-more-info");
            //when Full Details is clicked, event in the Calendar tool is shown.
            $("#fullDetails").html("<a href=" + more_info + " target=_top>" + fullDetailsText + "</a>");
            //On event click dialog is opened near the event
            $("#calendarEventDialog").dialog({ modal: false, title: event.title, width:450,position:[jsEvent.pageX, jsEvent.pageY] });
        }
    });
});