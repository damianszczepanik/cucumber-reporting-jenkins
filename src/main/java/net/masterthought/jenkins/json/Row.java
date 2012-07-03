package net.masterthought.jenkins.json;

import java.util.Arrays;

public class Row {

    private String[] cells;

    public Row() {

    }
    
    public String[] getCells(){
      return cells;
    }

    @Override
    public String toString() {
        return "Row{" +
                "cells=" + (cells == null ? null : Arrays.asList(cells)) +
                '}';
    }
}
