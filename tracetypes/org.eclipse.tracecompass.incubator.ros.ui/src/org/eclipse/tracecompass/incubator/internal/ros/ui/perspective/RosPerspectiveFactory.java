/**********************************************************************
 * Copyright (c) 2018 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.ros.ui.perspective;

import org.eclipse.tracecompass.incubator.internal.ros.ui.views.nodes.RosNodesView;
import org.eclipse.tracecompass.incubator.internal.ros.ui.views.queues.RosQueuesView;
import org.eclipse.tracecompass.tmf.ui.project.wizards.NewTmfProjectWizard;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

/**
 * Perspective for ROS traces
 *
 * @author Christophe Bedard
 */
public class RosPerspectiveFactory implements IPerspectiveFactory {

    /** Perspective ID */
    private static final String ID = "org.eclipse.tracecompass.incubator.ros.ui.perspective"; //$NON-NLS-1$

    // ROS views
    private static final String NODES_VIEW_ID = RosNodesView.ID;
    private static final String QUEUES_VIEW_ID = RosQueuesView.ID;

    // Standard Eclipse views
    private static final String PROJECT_VIEW_ID = IPageLayout.ID_PROJECT_EXPLORER;

    @Override
    public void createInitialLayout(IPageLayout layout) {

        layout.setEditorAreaVisible(true);

        // Left
        IFolderLayout leftFolder = layout.createFolder(
                "leftFolder", IPageLayout.LEFT, 0.15f, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
        leftFolder.addView(PROJECT_VIEW_ID);

        // Right, top
        IFolderLayout rightTopFolder = layout.createFolder(
                "rightTopFolder", IPageLayout.TOP, 0.25f, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
        rightTopFolder.addView(NODES_VIEW_ID);

        // Right, bottom
        IFolderLayout rightBottomFolder = layout.createFolder(
                "rightBottomFolder", IPageLayout.BOTTOM, 0.30f, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
        rightBottomFolder.addView(QUEUES_VIEW_ID);

        layout.addPerspectiveShortcut(ID);
        layout.addNewWizardShortcut(NewTmfProjectWizard.ID);
    }
}
