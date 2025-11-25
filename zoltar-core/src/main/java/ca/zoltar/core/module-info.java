module ca.zoltar.core {
    requires java.sql;
    requires java.net.http;
    requires ca.zoltar.db;
    requires ca.zoltar.search;
    requires ca.zoltar.pubmed;
    requires ca.zoltar.util;
    requires org.slf4j;
    requires org.apache.pdfbox;
    requires com.fasterxml.jackson.databind;

    exports ca.zoltar.core;
    exports ca.zoltar.core.service;
}
