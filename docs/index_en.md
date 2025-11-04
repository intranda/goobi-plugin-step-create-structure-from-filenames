---
title: Structure data acquisition of images based on file names
identifier: intranda_step_create_structure_from_filenames
description: Step plugin to capture structural data from images
published: false
keywords:
    - Goobi workflow
    - Plugin
    - Step Plugin
---

## Introduction
This documentation explains the plugin for capturing structural data from images based on their file names.

## Installation
To be able to use the plugin, the following files must be installed:

```bash
/opt/digiverso/goobi/plugins/step/plugin-step-create-structure-from-filenames-base.jar
/opt/digiverso/goobi/config/plugin_intranda_step_create_structure_from_filenames.xml
```

## Overview and functionality
Once the plugin has been installed and configured, it can be used within a single Goobi step.

To do this, the plugin `intranda_step_create_structure_from_filenames` must be selected within the desired task. The `Automatic task` checkbox can also be selected.


![Configuration of the work step for using the plugin](screen1_en.png)

The plugin works as follows within the correctly configured workflow:

* If the plugin has been called within the workflow, it runs through all images in the master folder and creates a new structure element for each image.
* However, if an image contains a sequence of letters defined in the configuration file in the file name, then this image is added to the last structure element.

## Configuration
The plugin is configured in the file `plugin_intranda_step_create_structure_from_filenames.xml` as shown here:

{{CONFIG_CONTENT}}

{{CONFIG_DESCRIPTION_PROJECT_STEP}}

Parameter               | Explanation
------------------------|------------------------------------
type           | The type for the structure element to be created |
infix          | The sequence of letters that can appear in the file name, whereby the image is appended to the previous structure element|

