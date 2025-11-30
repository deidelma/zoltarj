module ca.zoltar.gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires ca.zoltar.core;
    requires ca.zoltar.db;
    requires ca.zoltar.util;
    requires java.sql;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;

    exports ca.zoltar.gui;

    opens ca.zoltar.gui.controller to javafx.fxml;
}
