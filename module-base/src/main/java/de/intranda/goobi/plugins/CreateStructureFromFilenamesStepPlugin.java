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

import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Step;
import org.goobi.beans.Process;

import de.sub.goobi.helper.StorageProvider;
import java.util.List;
import java.util.TreeMap;

import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;


@PluginImplementation
@Log4j2
public class CreateStructureFromFilenamesStepPlugin implements IStepPluginVersion2 {
    
    @Getter
    private String title = "intranda_step_create_structure_from_filenames";
    @Getter
    private Step step;
    private String structureElementType;
    private String filenameString;
    private String returnPath;
    private Process process;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        this.process = step.getProzess();
                
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        structureElementType = myconfig.getString("structureElementType", "default structureElementType");
        filenameString = myconfig.getString("filenameString", "default filenameString");
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
        List<String> fileList = null;
        TreeMap<String, List<String>> treeMap = new TreeMap<String, List<String>>();
        
        try {
        	// Get the directory and print it on the console
            foldername = process.getImagesOrigDirectory(false);
            
            // Get the names of the folders and sort them in a list and print the list on the console
            fileList = StorageProvider.getInstance().list(foldername);
            
            // Go through all filenames in the list
            for (String fileName : fileList) {
                int underscoreIndex = fileName.indexOf("_");
                
                if (underscoreIndex == -1) {
                	// Get or create a list associated with this modifiedFileName in the TreeMap
                    int pointIndex = fileName.indexOf(".");
                    String modifiedFileName = fileName.substring(0, pointIndex);
                    // 
                    List<String> list = treeMap.get(modifiedFileName);
                    if (list == null) {
                        list = new ArrayList<>();
                        treeMap.put(modifiedFileName, list);
                    }
                    list.add(fileName);
                } else {
                	// Remove everything after the first underscore
                    String modifiedFileName = fileName.substring(0, underscoreIndex);
                    
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
            System.out.println(treeMap);
            
        } catch (Exception e) {
            log.error("Error accessing images directory", e);
            return PluginReturnValue.ERROR;
        }
        
        log.info("CreateStructureFromFilenames step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }


}
