alter table lesson_builder_items add groupOwned smallint;
alter table lesson_builder_items add ownerGroups varchar(4000);
alter table lesson_builder_pages add groupid varchar(36);
alter table lesson_builder_student_pages add groupid varchar(36);