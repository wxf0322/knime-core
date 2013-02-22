/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2011
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * --------------------------------------------------------------------- *
 *
 */
package org.knime.core.node.workflow;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.knime.core.data.container.ContainerTable;
import org.knime.core.data.filestore.internal.WorkflowFileStoreHandlerRepository;
import org.knime.core.internal.ReferencedFile;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortType;
import org.knime.core.node.workflow.MetaNodeTemplateInformation.Role;
import org.knime.core.util.LockFailedException;

/**
 *
 * @author Bernd Wiswedel, University of Konstanz
 */
public class WorkflowPersistorVersion200 extends WorkflowPersistorVersion1xx {

    private static final String CFG_UIINFO_SUB_CONFIG = "ui_settings";
    /** Key for UI info's class name. */
    private static final String CFG_UIINFO_CLASS = "ui_classname";

    /** Key for workflow variables. */
    private static final String CFG_WKF_VARIABLES = "workflow_variables";

    /** key used to store the editor specific settings (since 2.6). */
    private static final String CFG_EDITOR_INFO = "workflow_editor_settings";

    /** Key for workflow template information. */
    private static final String CFG_TEMPLATE_INFO =
        "workflow_template_information";

    /** Key for credentials. */
    private static final String CFG_CREDENTIALS = "workflow_credentials";

    /** A Version representing a specific workflow format. This enum covers only
     * the version that this specific class can read (or write).
     * Ordinal numbering is important. */
    public static enum LoadVersion {
        // Don't modify order, ordinal number are important.
        /** Pre v2.0. */
        UNKNOWN("<unknown>"),
        /** Version 2.0.0 - 2.0.x. */
        V200("2.0.0"),
        /** Trunk version when 2.0.x was out, covers cluster and
         * server prototypes. Obsolete since 2009-08-12. */
        V210_Pre("2.0.1"),
        /** Version 2.1.x. */
        V210("2.1.0"),
        /** Version 2.2.x, introduces optional inputs, flow variable input
         * credentials, node local drop directory. */
        V220("2.2.0"),
        /** Version 2.3.x, introduces workflow annotations & switches. */
        V230("2.3.0"),
        /** Version 2.4.x, introduces meta node templates. */
        V240("2.4.0"),
        /** Version 2.5.x, lockable meta nodes, node-relative annotations. */
        V250("2.5.0"),
        /** Version 2.6.x, file store objects, grid information, node vendor
         * & plugin information.
         * @since 2.6  */
        V260("2.6.0");

        private final String m_versionString;

        private LoadVersion(final String str) {
            m_versionString = str;
        }

        /** @return The String representing the LoadVersion (workflow.knime). */
        public String getVersionString() {
            return m_versionString;
        }

        /** Get the load version for the version string or null if unknown.
         * @param string Version string (as in workflow.knime).
         * @return The LoadVersion or null.
         */
        static LoadVersion get(final String string) {
            for (LoadVersion e : values()) {
                if (e.m_versionString.equals(string)) {
                    return e;
                }
            }
            return null;
        }
    }
    static final LoadVersion VERSION_LATEST = LoadVersion.V260;

    static LoadVersion canReadVersionV200(final String versionString) {
        return LoadVersion.get(versionString);
    }

    /** Create persistor for load.
     * @param tableRep Table repository
     * @param fileStoreHandlerRepository ...
     * @param workflowKNIMEFile workflow.knime or template.knime file
     * @param loadHelper As required by meta persistor.
     * @param version Version must pass {@link #canReadVersionV200(String)}.
     * @param isProject Whether workflow to load is a project or a meta node.
     * @throws IllegalStateException If version string is unsupported.
     */
    WorkflowPersistorVersion200(final HashMap<Integer, ContainerTable> tableRep,
            final WorkflowFileStoreHandlerRepository fileStoreHandlerRepository,
            final ReferencedFile workflowKNIMEFile,
            final WorkflowLoadHelper loadHelper,
            final LoadVersion version, final boolean isProject) {
        super(tableRep, fileStoreHandlerRepository,
                new NodeContainerMetaPersistorVersion200(
                workflowKNIMEFile, loadHelper, version), version, isProject);
    }

    /** @return version that is saved, {@value #VERSION_LATEST}. */
    protected static LoadVersion getSaveVersion() {
        return VERSION_LATEST;
    }

    /** {@inheritDoc} */
    @Override
    public boolean mustComplainIfStateDoesNotMatch() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public List<FlowVariable> loadWorkflowVariables(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        if (!settings.containsKey(CFG_WKF_VARIABLES)) {
            return Collections.emptyList();
        }
        NodeSettingsRO wfmVarSub = settings.getNodeSettings(CFG_WKF_VARIABLES);
        List<FlowVariable> result = new ArrayList<FlowVariable>();
        for (String key : wfmVarSub.keySet()) {
            result.add(FlowVariable.load(wfmVarSub.getNodeSettings(key)));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    protected List<Credentials> loadCredentials(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        // no credentials in v2.1 and before
        if (getLoadVersion().ordinal() < LoadVersion.V220.ordinal()) {
            return Collections.emptyList();
        }
        NodeSettingsRO sub = settings.getNodeSettings(CFG_CREDENTIALS);
        List<Credentials> r = new ArrayList<Credentials>();
        Set<String> credsNameSet = new HashSet<String>();
        for (String key : sub.keySet()) {
            NodeSettingsRO child = sub.getNodeSettings(key);
            Credentials c = Credentials.load(child);
            if (!credsNameSet.add(c.getName())) {
                getLogger().warn("Duplicate credentials variable \""
                        + c.getName() + "\" -- ignoring it");
            } else {
                r.add(c);
            }
        }
        return r;
    }

    /** {@inheritDoc} */
    @Override
    protected List<WorkflowAnnotation> loadWorkflowAnnotations(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        // no credentials in v2.2 and before
        if (getLoadVersion().ordinal() < LoadVersion.V230.ordinal()) {
            return Collections.emptyList();
        }
        if (!settings.containsKey("annotations")) {
            return Collections.emptyList();
        }
        NodeSettingsRO annoSettings = settings.getNodeSettings("annotations");
        List<WorkflowAnnotation> result = new ArrayList<WorkflowAnnotation>();
        for (String key : annoSettings.keySet()) {
            NodeSettingsRO child = annoSettings.getNodeSettings(key);
            WorkflowAnnotation anno = new WorkflowAnnotation();
            anno.load(child, getLoadVersion());
            result.add(anno);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    protected String loadWorkflowName(final NodeSettingsRO set)
            throws InvalidSettingsException {
        return set.getString("name");
    }

    /** {@inheritDoc} */
    @Override
    protected WorkflowCipher loadWorkflowCipher(final LoadVersion loadVersion,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        // added in v2.5 - no check necessary
        if (getLoadVersion().ordinal() < LoadVersion.V250.ordinal()) {
            return WorkflowCipher.NULL_CIPHER;
        }
        if (!settings.containsKey("cipher")) {
            return WorkflowCipher.NULL_CIPHER;
        }
            NodeSettingsRO cipherSettings = settings.getNodeSettings("cipher");
            return WorkflowCipher.load(loadVersion, cipherSettings);
        }

    /** {@inheritDoc} */
    @Override
    protected MetaNodeTemplateInformation loadTemplateInformation(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        if (settings.containsKey(CFG_TEMPLATE_INFO)) {
            NodeSettingsRO s = settings.getNodeSettings(CFG_TEMPLATE_INFO);
            return MetaNodeTemplateInformation.load(s, getLoadVersion());
        } else {
            return MetaNodeTemplateInformation.NONE;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected NodeSettingsRO readParentSettings() {
        return null; // only used in 1.3.x
    }

    /** {@inheritDoc} */
    @Override
    protected boolean loadIsMetaNode(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return settings.getBoolean("node_is_meta");
    }

    /** {@inheritDoc} */
    @Override
    protected String loadUIInfoClassName(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        if (settings.containsKey(CFG_UIINFO_CLASS)) {
            return settings.getString(CFG_UIINFO_CLASS);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected void loadUIInfoSettings(final UIInformation uiInfo,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        // in previous releases, the settings were directly written to the
        // top-most node settings object; since 2.0 they've been put into a
        // separate sub-settings object
        NodeSettingsRO subSettings =
            settings.getNodeSettings(CFG_UIINFO_SUB_CONFIG);
        super.loadUIInfoSettings(uiInfo, subSettings);
    }

    /** {@inheritDoc} */
    @Override
    protected int loadConnectionDestID(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return settings.getInt("destID");
    }

    /** {@inheritDoc} */
    @Override
    protected int loadConnectionDestPort(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        // possibly port index correction in fixDestPort method
        return settings.getInt("destPort");
    }

    /** {@inheritDoc} */
    @Override
    protected void fixDestPortIfNecessary(
            final NodeContainerPersistor destPersistor,
            final ConnectionContainerTemplate c) {
        // v2.1 and before did not have flow variable ports (index 0)
        if (getLoadVersion().ordinal() < LoadVersion.V220.ordinal()) {
            if (destPersistor
                    instanceof SingleNodeContainerPersistorVersion1xx) {
                // correct port index only for ordinary nodes (no new flow
                // variable ports on meta nodes)
                int index = c.getDestPort();
                c.setDestPort(index + 1);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void fixSourcePortIfNecessary(
            final NodeContainerPersistor sourcePersistor,
            final ConnectionContainerTemplate c) {
        // v2.1 and before did not have flow variable ports (index 0)
        if (getLoadVersion().ordinal() < LoadVersion.V220.ordinal()) {
            super.fixSourcePortIfNecessary(sourcePersistor, c);
        }
    }

    @Override
    protected NodeSettingsRO loadInPortsSetting(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        if (settings.containsKey("meta_in_ports")) {
            return settings.getNodeSettings("meta_in_ports");
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected NodeSettingsRO loadInPortsSettingsEnum(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        return settings.getNodeSettings("port_enum");
    }

    /** {@inheritDoc} */
    @Override
    protected WorkflowPortTemplate loadInPortTemplate(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        int index = settings.getInt("index");
        String name = settings.getString("name");
        NodeSettingsRO portTypeSettings = settings.getNodeSettings("type");
        PortType type = PortType.load(portTypeSettings);
        WorkflowPortTemplate result = new WorkflowPortTemplate(index, type);
        result.setPortName(name);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    protected String loadInPortsBarUIInfoClassName(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        return loadUIInfoClassName(settings);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadInPortsBarUIInfo(final UIInformation uiInfo,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        loadUIInfoSettings(uiInfo, settings);
    }

    /** {@inheritDoc} */
    @Override
    protected String loadOutPortsBarUIInfoClassName(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        return loadUIInfoClassName(settings);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadOutPortsBarUIInfo(final UIInformation uiInfo,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        loadUIInfoSettings(uiInfo, settings);
    }

    @Override
    protected NodeSettingsRO loadOutPortsSetting(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        if (settings.containsKey("meta_out_ports")) {
            return settings.getNodeSettings("meta_out_ports");
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected NodeSettingsRO loadOutPortsSettingsEnum(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        return settings.getNodeSettings("port_enum");
    }

    /** {@inheritDoc} */
    @Override
    protected WorkflowPortTemplate loadOutPortTemplate(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        int index = settings.getInt("index");
        String name = settings.getString("name");
        NodeSettingsRO portTypeSettings = settings.getNodeSettings("type");
        PortType type = PortType.load(portTypeSettings);
        WorkflowPortTemplate result = new WorkflowPortTemplate(index, type);
        result.setPortName(name);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    EditorUIInformation loadEditorUIInformation(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        final LoadVersion loadVersion = getLoadVersion();
        if (loadVersion.ordinal() < LoadVersion.V260.ordinal()
                || !settings.containsKey(CFG_EDITOR_INFO)) {
            return new EditorUIInformation();
        }
        NodeSettingsRO editorCfg = settings.getNodeSettings(CFG_EDITOR_INFO);
        EditorUIInformation editorInfo = new EditorUIInformation();
        editorInfo.load(editorCfg, loadVersion);
        return editorInfo;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean loadIfMustWarnOnDataLoadError(final File workflowDir) {
        return new File(workflowDir, SAVED_WITH_DATA_FILE).isFile();
    }

    protected static void saveUIInfoClassName(final NodeSettingsWO settings,
            final UIInformation info) {
        settings.addString(CFG_UIINFO_CLASS, info != null
                ? info.getClass().getName() : null);
    }

    protected static void saveUIInfoSettings(final NodeSettingsWO settings,
            final UIInformation uiInfo) {
        if (uiInfo == null) {
            return;
        }
        // nest into separate sub config
        NodeSettingsWO subConfig =
            settings.addNodeSettings(CFG_UIINFO_SUB_CONFIG);
        uiInfo.save(subConfig);
    }

    /** {@inheritDoc} */
    @Override
    protected WorkflowPersistorVersion200 createWorkflowPersistorLoad(
            final ReferencedFile wfmFile) {
        return new WorkflowPersistorVersion200(getGlobalTableRepository(),
                getFileStoreHandlerRepository(),
                wfmFile, getLoadHelper(), getLoadVersion(), false);
    }

    /** {@inheritDoc} */
    @Override
    protected SingleNodeContainerPersistorVersion200
        createSingleNodeContainerPersistorLoad(final ReferencedFile nodeFile) {
        return new SingleNodeContainerPersistorVersion200(
                this, nodeFile, getLoadHelper(), getLoadVersion());
    }

    public static String save(final WorkflowManager wm,
            final ReferencedFile workflowDirRef, final ExecutionMonitor execMon,
            final boolean isSaveData) throws IOException,
            CanceledExecutionException, LockFailedException {
        Role r = wm.getTemplateInformation().getRole();
        String fName = Role.Template.equals(r) ? TEMPLATE_FILE : WORKFLOW_FILE;
        fName = wm.getParent().getCipherFileName(fName);
        if (!workflowDirRef.fileLockRootForVM()) {
            throw new LockFailedException("Can't write workflow to \""
                    + workflowDirRef
                    + "\" because the directory can't be locked");
        }
        try {
            if (workflowDirRef.equals(wm.getNodeContainerDirectory()) && !wm.isDirty()) {
                return fName;
            }
            // delete "old" node directories if not saving to the working
            // directory -- do this before saving the nodes (dirs newly created)
            if (workflowDirRef.equals(wm.getNodeContainerDirectory())) {
                wm.deleteObsoleteNodeDirs();
            }
            File workflowDir = workflowDirRef.getFile();
            workflowDir.mkdirs();
            if (!workflowDir.isDirectory()) {
                throw new IOException("Unable to create or write directory \": "
                        + workflowDir + "\"");
            }
            NodeSettings settings = new NodeSettings(fName);
            settings.addString(
                    WorkflowManager.CFG_CREATED_BY, KNIMEConstants.VERSION);
            settings.addString(WorkflowManager.CFG_VERSION,
                    getSaveVersion().getVersionString());
            saveWorkflowName(settings, wm.getNameField());
            saveWorkflowCipher(settings, wm.getWorkflowCipher());
            saveTemplateInformation(wm.getTemplateInformation(), settings);
                NodeContainerMetaPersistorVersion200.save(
                        settings, wm, workflowDirRef);
            saveWorkflowVariables(wm, settings);
            saveCredentials(wm, settings);
            saveWorkflowAnnotations(wm, settings);

            NodeSettingsWO nodesSettings = saveSettingsForNodes(settings);
            Collection<NodeContainer> nodes = wm.getNodeContainers();
            double progRatio = 1.0 / (nodes.size() + 1);

            for (NodeContainer nextNode : nodes) {
                int id = nextNode.getID().getIndex();
                ExecutionMonitor subExec = execMon.createSubProgress(progRatio);
                execMon.setMessage(nextNode.getNameWithID());
                    NodeSettingsWO sub =
                        nodesSettings.addNodeSettings("node_" + id);
                saveNodeContainer(
                        sub, workflowDirRef, nextNode, subExec, isSaveData);
                subExec.setProgress(1.0);
            }

            execMon.setMessage("connection information");
            NodeSettingsWO connSettings = saveSettingsForConnections(settings);
            int connectionNumber = 0;
            for (ConnectionContainer cc : wm.getConnectionContainers()) {
                NodeSettingsWO nextConnectionConfig = connSettings
                        .addNodeSettings("connection_" + connectionNumber);
                saveConnection(nextConnectionConfig, cc);
                connectionNumber += 1;
            }
            int inCount = wm.getNrInPorts();
            NodeSettingsWO inPortsSetts = inCount > 0
                ? saveInPortsSetting(settings) : null;
            NodeSettingsWO inPortsSettsEnum = null;
            if (inPortsSetts != null) {
                saveInportsBarUIInfoClassName(
                        inPortsSetts, wm.getInPortsBarUIInfo());
                saveInportsBarUIInfoSettings(
                        inPortsSetts, wm.getInPortsBarUIInfo());
                inPortsSettsEnum = saveInPortsEnumSetting(inPortsSetts);
            }
            for (int i = 0; i < inCount; i++) {
                NodeSettingsWO sPort = saveInPortSetting(inPortsSettsEnum, i);
                saveInPort(sPort, wm, i);
            }
            int outCount = wm.getNrOutPorts();
            NodeSettingsWO outPortsSetts = outCount > 0
                ? saveOutPortsSetting(settings) : null;
            NodeSettingsWO outPortsSettsEnum = null;
            if (outPortsSetts != null) {
                saveOutportsBarUIInfoClassName(
                        outPortsSetts, wm.getOutPortsBarUIInfo());
                saveOutportsBarUIInfoSettings(
                        outPortsSetts, wm.getOutPortsBarUIInfo());
                outPortsSettsEnum = saveOutPortsEnumSetting(outPortsSetts);
            }
            for (int i = 0; i < outCount; i++) {
                NodeSettingsWO singlePort =
                    saveOutPortSetting(outPortsSettsEnum, i);
                saveOutPort(singlePort, wm, i);
            }
            saveEditorUIInformation(wm, settings);

            File workflowFile = new File(workflowDir, fName);
            String toBeDeletedFileName = Role.Template.equals(r) ? TEMPLATE_FILE : WORKFLOW_FILE;
            new File(workflowDir, toBeDeletedFileName).delete();
            new File(workflowDir, WorkflowCipher.getCipherFileName(toBeDeletedFileName)).delete();

            OutputStream os = new FileOutputStream(workflowFile);
            os = wm.getParent().cipherOutput(os);
            settings.saveToXML(os);
            File saveWithDataFile = new File(workflowDir, SAVED_WITH_DATA_FILE);
                BufferedWriter o =
                    new BufferedWriter(new FileWriter(saveWithDataFile));
            o.write("Do not delete this file!");
            o.newLine();
                o.write("This file serves to indicate that the workflow was "
                    + "written as part of the usual save routine "
                    + "(not exported).");
            o.newLine();
            o.newLine();
            o.write("Workflow was last saved by user ");
            o.write(System.getProperty("user.name"));
            o.write(" on " + new Date());
            o.close();
            if (wm.getNodeContainerDirectory() == null) {
                wm.setNodeContainerDirectory(workflowDirRef);
            }
            if (workflowDirRef.equals(wm.getNodeContainerDirectory())) {
                wm.unsetDirty();
            }
            execMon.setProgress(1.0);
            return fName;
        } finally {
            workflowDirRef.fileUnlockRootForVM();
        }
    }

    protected static void saveWorkflowName(
            final NodeSettingsWO settings, final String name) {
        settings.addString("name", name);
    }

    /** Metanode locking information.
     * @param settings
     * @param workflowCipher */
    protected static void saveWorkflowCipher(final NodeSettings settings,
            final WorkflowCipher workflowCipher) {
        if (!workflowCipher.isNullCipher()) {
            NodeSettingsWO cipherSettings = settings.addNodeSettings("cipher");
            workflowCipher.save(cipherSettings);
        }
    }

    protected static void saveTemplateInformation(
            final MetaNodeTemplateInformation mnti,
            final NodeSettingsWO settings) {
        switch (mnti.getRole()) {
        case None:
            // don't save
            break;
        default:
            NodeSettingsWO s = settings.addNodeSettings(CFG_TEMPLATE_INFO);
            mnti.save(s);
        }
    }

    /**
     * @param settings
     * @since 2.6 */
    static void saveEditorUIInformation(final WorkflowManager wfm,
            final NodeSettings settings) {
        EditorUIInformation editorInfo = wfm.getEditorUIInformation();
        if (editorInfo != null) {
            NodeSettingsWO editorCfg =
                settings.addNodeSettings(CFG_EDITOR_INFO);
            editorInfo.save(editorCfg);
        }
    }

    protected static void saveWorkflowVariables(final WorkflowManager wfm,
            final NodeSettingsWO settings) {
        List<FlowVariable> vars = wfm.getWorkflowVariables();
        if (!vars.isEmpty()) {
            NodeSettingsWO wfmVarSub =
                settings.addNodeSettings(CFG_WKF_VARIABLES);
            int i = 0;
            for (FlowVariable v : vars) {
                v.save(wfmVarSub.addNodeSettings("Var_" + (i++)));
            }
        }
    }

    protected static void saveCredentials(final WorkflowManager wfm,
            final NodeSettingsWO settings) {
        CredentialsStore credentialsStore = wfm.getCredentialsStore();
        NodeSettingsWO sub = settings.addNodeSettings(CFG_CREDENTIALS);
        synchronized (credentialsStore) {
            for (Credentials c : credentialsStore.getCredentials()) {
                NodeSettingsWO s = sub.addNodeSettings(c.getName());
                c.save(s);
            }
        }
    }

    protected static void saveWorkflowAnnotations(final WorkflowManager manager,
            final NodeSettingsWO settings) {
        Collection<WorkflowAnnotation> annotations =
            manager.getWorkflowAnnotations();
        if (annotations.size() == 0) {
            return;
        }
        NodeSettingsWO annoSettings = settings.addNodeSettings("annotations");
        int i = 0;
        for (Annotation a : annotations) {
            NodeSettingsWO t = annoSettings.addNodeSettings("annotation_" + i);
            a.save(t);
            i += 1;
        }
    }

    /**
     * Save nodes in an own sub-config object as a series of configs.
     *
     * @param settings To save to.
     * @return The sub config where subsequent writing takes place.
     */
    protected static NodeSettingsWO saveSettingsForNodes(final NodeSettingsWO settings) {
        return settings.addNodeSettings(KEY_NODES);
    }

    /**
     * Save connections in an own sub-config object.
     *
     * @param settings To save to.
     * @return The sub config where subsequent writing takes place.
     */
    protected static NodeSettingsWO saveSettingsForConnections(
            final NodeSettingsWO settings) {
        return settings.addNodeSettings(KEY_CONNECTIONS);
    }

    protected static void saveNodeContainer(final NodeSettingsWO settings,
            final ReferencedFile workflowDirRef, final NodeContainer container,
            final ExecutionMonitor exec, final boolean isSaveData)
            throws CanceledExecutionException, IOException, LockFailedException {
        WorkflowManager parent = container.getParent();
        ReferencedFile workingDir = parent.getNodeContainerDirectory();
        boolean isWorkingDir = workflowDirRef.equals(workingDir);

        saveNodeIDSuffix(settings, container);
        int idSuffix = container.getID().getIndex();

        // name of sub-directory container node/sub-workflow settings
        // all chars which are not letter or number are replaced by '_'
        String nodeDirID = container.getName().replaceAll("[^a-zA-Z0-9 ]", "_");
        int maxLength = 12;
        // bug fix 3576 -- long meta node names are problematic on windows file system
        if (container instanceof WorkflowManager && nodeDirID.length() > maxLength) {
            nodeDirID = nodeDirID.substring(0, maxLength).trim();
        }
        nodeDirID = nodeDirID.concat(" (#" + idSuffix + ")");

        // try to re-use previous node dir (might be different from calculated
        // one above in case node was renamed between releases)
        if (isWorkingDir && container.getNodeContainerDirectory() != null) {
            ReferencedFile ncDirectory = container.getNodeContainerDirectory();
            nodeDirID = ncDirectory.getFile().getName();
        }

        ReferencedFile nodeDirectoryRef =
            new ReferencedFile(workflowDirRef, nodeDirID);
        String fileName;
        if (container instanceof WorkflowManager) {
            fileName = WorkflowPersistorVersion200.save(
                    (WorkflowManager)container,
                    nodeDirectoryRef, exec, isSaveData);
        } else {
            fileName = SingleNodeContainerPersistorVersion200.save(
                    (SingleNodeContainer)container,
                    nodeDirectoryRef, exec, isSaveData);
        }
        saveFileLocation(settings, nodeDirID + "/" + fileName);
        saveIsMeta(settings, container);
        saveUIInfoClassName(settings, container.getUIInformation());
        saveUIInfoSettings(settings, container.getUIInformation());
    }

    protected static void saveNodeIDSuffix(final NodeSettingsWO settings,
            final NodeContainer nc) {
        settings.addInt(KEY_ID, nc.getID().getIndex());
    }

    protected static void saveFileLocation(final NodeSettingsWO settings,
            final String location) {
        settings.addString("node_settings_file", location);
    }

    protected static void saveIsMeta(
            final NodeSettingsWO settings, final NodeContainer nc) {
        settings.addBoolean("node_is_meta", nc instanceof WorkflowManager);
    }

    protected static NodeSettingsWO saveInPortsSetting(final NodeSettingsWO settings) {
        return settings.addNodeSettings("meta_in_ports");
    }

    protected static NodeSettingsWO saveInPortsEnumSetting(
            final NodeSettingsWO settings) {
        return settings.addNodeSettings("port_enum");
    }

    protected static NodeSettingsWO saveInPortSetting(
            final NodeSettingsWO settings, final int portIndex) {
        return settings.addNodeSettings("inport_" + portIndex);
    }

    protected static void saveInportsBarUIInfoClassName(final NodeSettingsWO settings,
            final UIInformation info) {
        saveUIInfoClassName(settings, info);
    }

    protected static void saveInportsBarUIInfoSettings(final NodeSettingsWO settings,
            final UIInformation uiInfo) {
        saveUIInfoSettings(settings, uiInfo);
    }

    protected static void saveInPort(final NodeSettingsWO settings,
            final WorkflowManager wm, final int portIndex) {
        WorkflowInPort inport = wm.getInPort(portIndex);
        settings.addInt("index", portIndex);
        settings.addString("name", inport.getPortName());
        NodeSettingsWO portTypeSettings = settings.addNodeSettings("type");
        inport.getPortType().save(portTypeSettings);
    }

    protected static NodeSettingsWO saveOutPortsSetting(
            final NodeSettingsWO settings) {
        return settings.addNodeSettings("meta_out_ports");
    }

    protected static NodeSettingsWO saveOutPortsEnumSetting(
            final NodeSettingsWO settings) {
        return settings.addNodeSettings("port_enum");
    }

    protected static void saveOutportsBarUIInfoClassName(final NodeSettingsWO settings,
            final UIInformation info) {
        saveUIInfoClassName(settings, info);
    }

    protected static void saveOutportsBarUIInfoSettings(final NodeSettingsWO settings,
            final UIInformation uiInfo) {
        saveUIInfoSettings(settings, uiInfo);
    }

    protected static NodeSettingsWO saveOutPortSetting(
            final NodeSettingsWO settings, final int portIndex) {
        return settings.addNodeSettings("outport_" + portIndex);
    }

    protected static void saveOutPort(final NodeSettingsWO settings,
            final WorkflowManager wm, final int portIndex) {
        WorkflowOutPort outport = wm.getOutPort(portIndex);
        settings.addInt("index", portIndex);
        settings.addString("name", outport.getPortName());
        NodeSettingsWO portTypeSettings = settings.addNodeSettings("type");
        outport.getPortType().save(portTypeSettings);
    }

    protected static void saveConnection(final NodeSettingsWO settings,
            final ConnectionContainer connection) {
        int sourceID = connection.getSource().getIndex();
        int destID = connection.getDest().getIndex();
        switch (connection.getType()) {
        case WFMIN:
            sourceID = -1;
            break;
        case WFMOUT:
            destID = -1;
            break;
        case WFMTHROUGH:
            sourceID = -1;
            destID = -1;
            break;
        default:
            // all handled above
        }
        settings.addInt("sourceID", sourceID);
        settings.addInt("destID", destID);
        int sourcePort = connection.getSourcePort();
        settings.addInt("sourcePort", sourcePort);
        int targetPort = connection.getDestPort();
        settings.addInt("destPort", targetPort);
        UIInformation uiInfo = connection.getUIInfo();
        if (uiInfo != null) {
            saveUIInfoClassName(settings, uiInfo);
            saveUIInfoSettings(settings, uiInfo);
        }
        if (!connection.isDeletable()) {
            settings.addBoolean("isDeletable", false);
        }
    }


}
