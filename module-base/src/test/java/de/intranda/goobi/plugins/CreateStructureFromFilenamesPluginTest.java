package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.beans.User;
import org.goobi.production.enums.PluginReturnValue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.dl.Reference;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetadatenHelper.class, VariableReplacer.class, ConfigurationHelper.class, ProcessManager.class,
        MetadataManager.class, Helper.class })
@PowerMockIgnore({ "javax.management.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class CreateStructureFromFilenamesPluginTest {

    private static String resourcesFolder;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File processDirectory;
    private File metadataDirectory;
    private Process process;
    private Step step;
    private Prefs prefs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Test
    public void testConstructor() throws Exception {
        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testInit() {
        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "something");
        assertEquals(step.getTitel(), plugin.getStep().getTitel());
    }

    @Test
    public void runCreatesOneChapterPerUniqueFilenameStem() throws Exception {
        writeMasterFiles("001.tif", "002.tif", "003.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        PluginReturnValue result = plugin.run();
        assertEquals(PluginReturnValue.FINISH, result);

        DigitalDocument dd = reloadDocument();
        List<DocStruct> chapterList = chapters(dd);
        assertEquals(3, chapterList.size());
        for (DocStruct ch : chapterList) {
            assertEquals(1, pagesOf(ch).size());
        }
        assertEquals(3, dd.getPhysicalDocStruct().getAllChildren().size());
    }

    @Test
    public void runGroupsBackprintWithItsBaseFile() throws Exception {
        writeMasterFiles("001.tif", "001_backprint.tif", "002.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        plugin.run();

        DigitalDocument dd = reloadDocument();
        List<DocStruct> chapterList = chapters(dd);
        assertEquals(2, chapterList.size());
        assertEquals(2, pagesOf(chapterList.get(0)).size());
        assertEquals(1, pagesOf(chapterList.get(1)).size());
    }

    @Test
    public void runOrdersBackprintAfterBaseWithinGroup() throws Exception {
        writeMasterFiles("001.tif", "001_backprint.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        plugin.run();

        DigitalDocument dd = reloadDocument();
        List<DocStruct> pages = pagesOf(chapters(dd).get(0));
        assertEquals(2, pages.size());
        assertTrue(contentFilePath(pages.get(0)).endsWith("001.tif"));
        assertTrue(contentFilePath(pages.get(1)).endsWith("001_backprint.tif"));
    }

    @Test
    public void runSortsChaptersByPrefixAlphabetically() throws Exception {
        writeMasterFiles("c.tif", "a.tif", "b.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        plugin.run();

        DigitalDocument dd = reloadDocument();
        List<DocStruct> chapterList = chapters(dd);
        assertEquals(3, chapterList.size());
        assertTrue(contentFilePath(pagesOf(chapterList.get(0)).get(0)).endsWith("a.tif"));
        assertTrue(contentFilePath(pagesOf(chapterList.get(1)).get(0)).endsWith("b.tif"));
        assertTrue(contentFilePath(pagesOf(chapterList.get(2)).get(0)).endsWith("c.tif"));
    }

    @Test
    public void runSetsContentFileMimetypeAndAbsolutePath() throws Exception {
        writeMasterFiles("001.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        plugin.run();

        DigitalDocument dd = reloadDocument();
        DocStruct page = pagesOf(chapters(dd).get(0)).get(0);
        ContentFile cf = page.getAllContentFiles().get(0);
        assertEquals("image/tiff", cf.getMimetype());
        File expected = new File(processDirectory,
                "images" + File.separator + "00469418X_master" + File.separator + "001.tif");
        assertEquals(expected.getAbsolutePath(), cf.getLocation());
    }

    @Test
    public void runAssignsSequentialPageNumbers() throws Exception {
        writeMasterFiles("001.tif", "002.tif", "003.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        plugin.run();

        DigitalDocument dd = reloadDocument();
        List<DocStruct> physPages = dd.getPhysicalDocStruct().getAllChildren();
        assertEquals(3, physPages.size());
        String[] expected = { "1", "2", "3" };
        for (int i = 0; i < expected.length; i++) {
            DocStruct page = physPages.get(i);
            assertEquals(expected[i], findMetadata(page, "physPageNumber"));
            assertEquals(expected[i], findMetadata(page, "logicalPageNumber"));
        }
    }

    @Test
    public void runReturnsFinishOnSuccess() throws Exception {
        writeMasterFiles("001.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        assertEquals(PluginReturnValue.FINISH, plugin.run());
    }

    @Test
    public void executeReturnsTrueOnSuccess() throws Exception {
        writeMasterFiles("001.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        assertTrue(plugin.execute());
    }

    @Test
    public void runIsIdempotentWhenInvokedTwice() throws Exception {
        writeMasterFiles("001.tif", "002.tif");

        CreateStructureFromFilenamesStepPlugin plugin = new CreateStructureFromFilenamesStepPlugin();
        plugin.initialize(step, "");
        plugin.run();

        plugin.initialize(step, "");
        plugin.run();

        DigitalDocument dd = reloadDocument();
        assertEquals(2, chapters(dd).size());
        assertEquals(2, dd.getPhysicalDocStruct().getAllChildren().size());
    }

    @Before
    public void setUp() throws Exception {
        metadataDirectory = folder.newFolder("metadata");
        processDirectory = new File(metadataDirectory + File.separator + "1");
        processDirectory.mkdirs();
        String metadataDirectoryName = metadataDirectory.getAbsolutePath() + File.separator;
        Path metaSource = Paths.get(resourcesFolder, "meta.xml");
        Path metaTarget = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Files.copy(metaSource, metaTarget);

        Path anchorSource = Paths.get(resourcesFolder, "meta_anchor.xml");
        Path anchorTarget = Paths.get(processDirectory.getAbsolutePath(), "meta_anchor.xml");
        Files.copy(anchorSource, anchorTarget);

        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();
        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectoryName).anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("00469418X_media").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMasterDirectoryName()).andReturn("{processtitle}_master").anyTimes();
        EasyMock.expect(configurationHelper.isUseMasterDirectory()).andReturn(true).anyTimes();
        EasyMock.expect(configurationHelper.isCreateMasterDirectory()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getNumberOfMetaBackups()).andReturn(0).anyTimes();
        EasyMock.replay(configurationHelper);

        PowerMock.mockStatic(VariableReplacer.class);
        EasyMock.expect(VariableReplacer.simpleReplace(EasyMock.anyString(), EasyMock.anyObject())).andAnswer(() -> {
            Object[] args = EasyMock.getCurrentArguments();
            String template = (String) args[0];
            if (template == null) {
                return null;
            }
            return template.replace("{processtitle}", "00469418X");
        }).anyTimes();
        PowerMock.replay(VariableReplacer.class);
        prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        Fileformat ff = new MetsMods(prefs);
        ff.read(metaTarget.toString());

        PowerMock.mockStatic(MetadatenHelper.class);
        EasyMock.expect(MetadatenHelper.getMetaFileType(EasyMock.anyString())).andReturn("mets").anyTimes();
        EasyMock.expect(MetadatenHelper.getFileformatByName(EasyMock.anyString(), EasyMock.anyObject())).andReturn(ff).anyTimes();
        EasyMock.expect(MetadatenHelper.getMetadataOfFileformat(EasyMock.anyObject(), EasyMock.anyBoolean()))
                .andReturn(Collections.emptyMap())
                .anyTimes();
        PowerMock.replay(MetadatenHelper.class);

        PowerMock.mockStatic(MetadataManager.class);
        MetadataManager.updateMetadata(EasyMock.anyInt(), EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        MetadataManager.updateJSONMetadata(EasyMock.anyInt(), EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        PowerMock.replay(MetadataManager.class);
        PowerMock.replay(ConfigurationHelper.class);

        PowerMock.mockStaticPartial(Helper.class, "addMessageToProcessJournal");
        Helper.addMessageToProcessJournal(EasyMock.anyInt(), EasyMock.anyObject(), EasyMock.anyString());
        EasyMock.expectLastCall().anyTimes();
        PowerMock.replay(Helper.class);

        process = getProcess();

        Ruleset ruleset = PowerMock.createMock(Ruleset.class);
        ruleset.setTitel("ruleset");
        ruleset.setDatei("ruleset.xml");
        EasyMock.expect(ruleset.getDatei()).andReturn("ruleset.xml").anyTimes();
        process.setRegelsatz(ruleset);
        EasyMock.expect(ruleset.getPreferences()).andReturn(prefs).anyTimes();
        PowerMock.replay(ruleset);

    }

    public Process getProcess() {
        Project project = new Project();
        project.setTitel("CreateStructureFromFilenamesProject");

        Process process = new Process();
        process.setTitel("00469418X");
        process.setProjekt(project);
        process.setId(1);
        List<Step> steps = new ArrayList<>();
        step = new Step();
        step.setReihenfolge(1);
        step.setProzess(process);
        step.setProcessId(1);
        step.setTitel("test step");
        step.setBearbeitungsstatusEnum(StepStatus.OPEN);
        User user = new User();
        user.setVorname("Firstname");
        user.setNachname("Lastname");
        step.setBearbeitungsbenutzer(user);
        steps.add(step);

        process.setSchritte(steps);

        try {
            createProcessDirectory(processDirectory);
        } catch (IOException e) {
        }

        return process;
    }

    private void createProcessDirectory(File processDirectory) throws IOException {

        // image folder
        File imageDirectory = new File(processDirectory.getAbsolutePath(), "images");
        imageDirectory.mkdir();
        // master folder
        File masterDirectory = new File(imageDirectory.getAbsolutePath(), "00469418X_master");
        masterDirectory.mkdir();

        // media folder
        File mediaDirectory = new File(imageDirectory.getAbsolutePath(), "00469418X_media");
        mediaDirectory.mkdir();

        // TODO add some file
    }

    private void writeMasterFiles(String... names) throws IOException {
        File master = new File(processDirectory, "images" + File.separator + "00469418X_master");
        for (String name : names) {
            Files.createFile(master.toPath().resolve(name));
        }
    }

    private DigitalDocument reloadDocument() throws Exception {
        Fileformat ff = new MetsMods(prefs);
        ff.read(processDirectory.getAbsolutePath() + File.separator + "meta.xml");
        return ff.getDigitalDocument();
    }

    private List<DocStruct> chapters(DigitalDocument dd) {
        DocStruct top = dd.getLogicalDocStruct();
        List<DocStruct> kids = top.getAllChildren();
        if (kids == null) {
            return Collections.emptyList();
        }
        List<DocStruct> result = new ArrayList<>();
        for (DocStruct ds : kids) {
            if ("Chapter".equals(ds.getType().getName())) {
                result.add(ds);
            }
        }
        return result;
    }

    private List<DocStruct> pagesOf(DocStruct chapter) {
        List<Reference> refs = chapter.getAllReferences("to");
        List<DocStruct> result = new ArrayList<>();
        if (refs != null) {
            for (Reference r : refs) {
                result.add(r.getTarget());
            }
        }
        return result;
    }

    private String contentFilePath(DocStruct page) {
        List<ContentFile> cfs = page.getAllContentFiles();
        if (cfs == null || cfs.isEmpty()) {
            return null;
        }
        return cfs.get(0).getLocation();
    }

    private String findMetadata(DocStruct page, String name) {
        if (page.getAllMetadata() == null) {
            return null;
        }
        for (Metadata m : page.getAllMetadata()) {
            if (m.getType().getName().equals(name)) {
                return m.getValue();
            }
        }
        return null;
    }
}
