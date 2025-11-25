module ca.zoltar.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires ca.zoltar.gui;
    requires ca.zoltar.core;
    requires ca.zoltar.db;
    requires ca.zoltar.util;
    requires org.slf4j;
    requires ch.qos.logback.classic;

    exports ca.zoltar.app;
}
