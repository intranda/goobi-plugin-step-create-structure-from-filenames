package de.intranda.goobi.plugins;

import java.io.File;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;

@PluginImplementation
@Log4j2
public class CreateStructureFromFilenamesStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_create_structure_from_filenames";
    @Getter
    private Step step;
    private String type;
    private String infix;
    private String returnPath;
    private Process process;
    private XMLConfiguration config;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        this.process = step.getProzess();

        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        type = myconfig.getString("type", "Chapter");
        infix = myconfig.getString("infix", "_backprint");
        log.info("CreateStructureFromFilenames step plugin initialized");
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_create_structure_from_filenames.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;

        // Creating new Lists
        String foldername = null;
        TreeMap<String, List<String>> treeMap = new TreeMap<String, List<String>>();

        try {
            // Get the directory
            foldername = process.getImagesOrigDirectory(false);

            // Get all files of the master folder
            List<String> fileList = StorageProvider.getInstance().list(foldername);

            // Put all backprints to the end of the list
            List<String> files = fileList.stream()
                    .filter(fileName -> !fileName.contains(infix))
                    .collect(Collectors.toList());
            List<String> backprintFiles = fileList.stream()
                    .filter(fileName -> fileName.contains(infix))
                    .collect(Collectors.toList());
            files.addAll(backprintFiles);

            // Go through all filenames in the list
            for (String fileName : files) {
                int separatorIndex = fileName.indexOf(infix);

                if (separatorIndex == -1) {
                    // Get or create a list associated with this modifiedFileName in the treeMap
                    int pointIndex = fileName.lastIndexOf(".");
                    String modifiedFileName = fileName.substring(0, pointIndex);
                    //
                    List<String> list = treeMap.get(modifiedFileName);
                    if (list == null) {
                        list = new ArrayList<>();
                        treeMap.put(modifiedFileName, list);
                    }
                    list.add(fileName);
                } else {
                    // Get everything before the filenameString
                    String modifiedFileName = fileName.substring(0, separatorIndex);

                    // Check if the modifiedFileName already exists in the treeMap
                    if (treeMap.containsKey(modifiedFileName)) {
                        // If it exists, add the original fileName to the list associated with modifiedFileName
                        treeMap.get(modifiedFileName).add(fileName);
                    } else {
                        // If it doesn't exist, create a new entry with modifiedFileName as the key
                        List<String> list = treeMap.get(modifiedFileName);
                        if (list == null) {
                            list = new ArrayList<>();
                            treeMap.put(modifiedFileName, list);
                        }
                        list.add(fileName);
                    }
                }
            }

            Process process = step.getProzess();
            Prefs prefs = process.getRegelsatz().getPreferences();
            Fileformat fileformat = process.readMetadataFile();
            DigitalDocument dd = fileformat.getDigitalDocument();

            // Find the structure elements to be updated
            DocStruct topstruct = dd.getLogicalDocStruct();

            // Go through the TreeMap and add each key as a child
            for (String key : treeMap.keySet()) {
                DocStruct childStruct = dd.createDocStruct(prefs.getDocStrctTypeByName(type));
                topstruct.addChild(childStruct);

                // Go through all files
                List<String> associatedFiles = treeMap.get(key);
                for (String fileName : associatedFiles) {
                    // Get the file name
                    File imageFile = new File(foldername, fileName);

                    // Create a new structure element for the page
                    DocStruct dsPage = dd.createDocStruct(prefs.getDocStrctTypeByName("page"));
                    dd.getPhysicalDocStruct().addChild(dsPage);

                    // Add physical page information
                    Metadata metaPhysPageNumber = new Metadata(prefs.getMetadataTypeByName("physPageNumber"));
                    metaPhysPageNumber.setValue(String.valueOf(dd.getPhysicalDocStruct().getAllChildren().size()));
                    dsPage.addMetadata(metaPhysPageNumber);

                    // Add logical page information
                    Metadata metaLogPageNumber = new Metadata(prefs.getMetadataTypeByName("logicalPageNumber"));
                    metaLogPageNumber.setValue(String.valueOf(dd.getPhysicalDocStruct().getAllChildren().size()));
                    dsPage.addMetadata(metaLogPageNumber);

                    // Create and add a content file
                    ContentFile cf = new ContentFile();
                    cf.setMimetype("image/tiff");
                    cf.setLocation(imageFile.getAbsolutePath());
                    dsPage.addContentFile(cf);

                    // Link the physical and logical structure elements
                    dd.getLogicalDocStruct().addReferenceTo(dsPage, "logical_physical");
                    childStruct.addReferenceTo(dsPage, "logical_physical");
                }
            }

            process.writeMetadataFile(fileformat);

        } catch (Exception e) {
            log.error("Error accessing images directory", e);
            return PluginReturnValue.ERROR;
        }

        log.info("CreateStructureFromFilenames step plugin executed");
        if (!successful) {
            Helper.addMessageToProcessJournal(step.getProcessId(), LogType.ERROR,
                    "CreateStructureFromFilenames plugin encountered an error while processing filenames and updating the document structure.");
            return PluginReturnValue.ERROR;
        } else {
            Helper.addMessageToProcessJournal(step.getProcessId(), LogType.INFO,
                    "CreateStructureFromFilenames plugin successfully processed filenames and updated the document structure.");
        }
        return PluginReturnValue.FINISH;
    }

}
