package org.hvdw.jexiftoolgui.editpane;

import org.hvdw.jexiftoolgui.MyConstants;
import org.hvdw.jexiftoolgui.MyVariables;
import org.hvdw.jexiftoolgui.Utils;
import org.hvdw.jexiftoolgui.controllers.CommandRunner;
import org.hvdw.jexiftoolgui.controllers.SQLiteJDBC;
import org.hvdw.jexiftoolgui.facades.IPreferencesFacade;
import org.hvdw.jexiftoolgui.facades.SystemPropertyFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static org.hvdw.jexiftoolgui.facades.IPreferencesFacade.PreferenceKey.PRESERVE_MODIFY_DATE;
import static org.hvdw.jexiftoolgui.facades.SystemPropertyFacade.SystemPropertyKey.LINE_SEPARATOR;
import static org.hvdw.jexiftoolgui.facades.SystemPropertyFacade.SystemPropertyKey.USER_HOME;

public class EditUserDefinedCombis {
    private final static ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EditUserDefinedCombis.class);
    private final static IPreferencesFacade prefs = IPreferencesFacade.defaultInstance;

    JTable usercombiTable;
    MyTableModel model;
    List<String> tablerowdata = new ArrayList<String>();
    String strcustomconfigfile = "";

    /*
    / This option makes only the 3rd column (0, 1, 2) editable
    / column 1 (no. 0) & 2 (no. 1) are ready-only
     */
    public class MyTableModel extends DefaultTableModel {
        public boolean isCellEditable(int row, int column){
            return column == 2;
        }

    }
    /*
    / This method updates the table in case we have selected another combi from the JCombobox
     */
    public void UpdateTable(JPanel rootpanel, JComboBox combicombobox, JScrollPane userCombiPane) {

        List<String> tablerowdata = new ArrayList<String>();
        usercombiTable = new JTable(new MyTableModel());

        model = ((MyTableModel) (usercombiTable.getModel()));
        model.setColumnIdentifiers(new String[]{ResourceBundle.getBundle("translations/program_strings").getString("mduc.columnlabel"),
                ResourceBundle.getBundle("translations/program_strings").getString("mduc.columntag"),
                ResourceBundle.getBundle("translations/program_strings").getString("mduc.columndefault")});
        model.setRowCount(0);
        Object[] row = new Object[1];

        String setName = combicombobox.getSelectedItem().toString();
        String sql = "select screen_label, tag, default_value from custommetadatasetLines where customset_name='" + setName.trim() + "' order by rowcount";
        String queryResult = SQLiteJDBC.generalQuery(sql);
        if (queryResult.length() > 0) {
            String[] lines = queryResult.split(SystemPropertyFacade.getPropertyByKey(LINE_SEPARATOR));

            for (String line : lines) {
                String[] cells = line.split("\\t", 4);
                model.addRow(new Object[]{cells[0], cells[1], cells[2]});
                tablerowdata.add(cells[2]);
            }
            MyVariables.setuserCombiTableValues(tablerowdata);
        }
        userCombiPane.setViewportView(usercombiTable);

        usercombiTable.getModel().addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                logger.debug("source {}; firstRow {}; lastRow {}, column{}", e.getSource(), e.getFirstRow(), e.getLastRow(), e.getColumn());
                logger.debug("tag {}; original value {}; modified tablecell value {}", model.getValueAt(e.getFirstRow(),1), tablerowdata.get(e.getFirstRow()), model.getValueAt(e.getFirstRow(),e.getColumn()));
            }
        });
    }

    public void UpdateCustomConfigLabel(JComboBox combicombobox, JLabel customconfiglabel) {
        String setName = combicombobox.getSelectedItem().toString();
        String sql = "select custom_config from custommetadataset where customset_name='" + setName.trim() + "'";
        String queryResult = SQLiteJDBC.singleFieldQuery(sql, "custom_config").trim();
        if ("".equals(queryResult) || queryResult.isEmpty() || "null".equals(queryResult)) {
            customconfiglabel.setText("");
            strcustomconfigfile = "";
            customconfiglabel.setVisible(false);
        } else {
            strcustomconfigfile = queryResult.trim();
            customconfiglabel.setText("This set is using custom config file: " + queryResult);
            customconfiglabel.setVisible(true);
        }
    }

    public void ResetFields(JScrollPane userCombiPane) {
        // Not used here: if user wants to reset, he/she simply selects the dropdown again.
    }

    public void SaveTableValues(JCheckBox udcOverwriteOriginalscheckBox, JProgressBar progressBar) {
        // if changed => save
        // else if !empty => save
        File[] files = MyVariables.getSelectedFiles();
        int[] selectedIndices = MyVariables.getSelectedFilenamesIndices();
        tablerowdata = MyVariables.getuserCombiTableValues();
        int rowcounter = 0;
        List<String> cmdparams = new ArrayList<String>();

        cmdparams.add(Utils.platformExiftool());
        // -config parameter for custom config file has to be first parameter on command line
        if (!strcustomconfigfile.equals("")) {
            String userHome = SystemPropertyFacade.getPropertyByKey(USER_HOME);
            String strjexiftoolguifolder = userHome + File.separator + MyConstants.MY_DATA_FOLDER;
            cmdparams.add("-config");
            cmdparams.add(strjexiftoolguifolder + File.separator + strcustomconfigfile);
        }
        boolean preserveModifyDate = prefs.getByKey(PRESERVE_MODIFY_DATE, false);
        if (preserveModifyDate) {
            cmdparams.add("-preserve");
        }
        if (!udcOverwriteOriginalscheckBox.isSelected()) {
            // default not select ->overwrite originals
            cmdparams.add("-overwrite_original");
        }
        cmdparams.addAll(Utils.AlwaysAdd());

        for (String value : tablerowdata) {
            if ( (!model.getValueAt(rowcounter, 2).equals(value)) || (!model.getValueAt(rowcounter, 2).equals("")) ) { //not equal, table value has changed OR table value not empty -> means default value
                logger.info("tag {}; original value {}; modified tablecell value {}", model.getValueAt(rowcounter,1), tablerowdata.get(rowcounter), model.getValueAt(rowcounter,2));
                if (model.getValueAt(rowcounter,1).toString().startsWith("-")) {
                    cmdparams.add(model.getValueAt(rowcounter,1).toString() + "=" + model.getValueAt(rowcounter, 2).toString().trim());
                } else { //tag without - (minus sign/hyphen) as prefix
                    cmdparams.add("-" + model.getValueAt(rowcounter,1).toString() + "=" + model.getValueAt(rowcounter, 2).toString().trim());
                }
            }
            rowcounter++;
        }

        for (int index: selectedIndices) {
            //logger.info("index: {}  image path: {}", index, files[index].getPath());
            if (Utils.isOsFromMicrosoft()) {
                cmdparams.add(files[index].getPath().replace("\\", "/"));
            } else {
                cmdparams.add(files[index].getPath());
            }
        }

        logger.info("Save user combi parameter command {}", cmdparams);
        CommandRunner.runCommandWithProgressBar(cmdparams, progressBar);
    }

    public void CopyFromSelectedImage() {
        File[] files = MyVariables.getSelectedFiles();
        int SelectedRow = MyVariables.getSelectedRow();
        tablerowdata = MyVariables.getuserCombiTableValues();
        int rowcounter = 0;
        String fpath ="";
        String res = "";
        List<String> cmdparams = new ArrayList<String>();
        List<String> tagnames = new ArrayList<String>();


        if (Utils.isOsFromMicrosoft()) {
            fpath = files[SelectedRow].getPath().replace("\\", "/");
        } else {
            fpath = files[SelectedRow].getPath();
        }
        cmdparams.add(Utils.platformExiftool());
        cmdparams.add("-e");
        cmdparams.add("-n");
        // Now get the tags from table
        for (String value : tablerowdata) {
            logger.debug("tag derived from table {}", model.getValueAt(rowcounter,1));
            if (model.getValueAt(rowcounter,1).toString().startsWith("-")) {
                cmdparams.add(model.getValueAt(rowcounter,1).toString().trim());
                tagnames.add(model.getValueAt(rowcounter,1).toString().trim().substring(1));
            } else { //tag without - (minus sign/hyphen) as prefix
                cmdparams.add("-" + model.getValueAt(rowcounter,1).toString().trim());
                tagnames.add(model.getValueAt(rowcounter,1).toString().trim());
            }
            rowcounter++;
        }
        cmdparams.add(fpath);
        try {
            res = CommandRunner.runCommand(cmdparams);
            logger.debug("res from copyfrom is\n{}", res);
        } catch(IOException | InterruptedException ex) {
            logger.debug("Error executing command");
        }
        if (res.length() > 0) {
            String[] strTagnames = tagnames.stream().toArray(String[]::new);
            displayCopiedInfo( res, strTagnames);
        }

    }

    private void displayCopiedInfo(String exiftoolInfo, String[] tagNames) {
        int rowcounter = 0;
        String[] lines = exiftoolInfo.split(SystemPropertyFacade.getPropertyByKey(LINE_SEPARATOR));
        tablerowdata = MyVariables.getuserCombiTableValues();

        for (String line : lines) {
            String[] returnedValuesRow = line.split(":", 2); // Only split on first : as some tags also contain (multiple) :
            String SpaceStrippedTag = returnedValuesRow[0].replaceAll("\\s+","");  // regex "\s" is space, extra \ to escape the first \
            logger.debug("returnedValuesRow tag {}; returnedValuesRow value {}; SpaceStrippedTag {}", returnedValuesRow[0], returnedValuesRow[1], SpaceStrippedTag);

            rowcounter =0;
            for (String tagname: tablerowdata) {
                if (model.getValueAt(rowcounter,1).toString().contains(SpaceStrippedTag)) {
                    model.setValueAt(returnedValuesRow[1].trim(),rowcounter,2);
                }
                rowcounter++;
            }
        }
    }
}
