module ca.zoltar.search {
    requires org.apache.lucene.core;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.analysis.common;
    requires org.slf4j;
    requires ca.zoltar.util;

    exports ca.zoltar.search;
}
