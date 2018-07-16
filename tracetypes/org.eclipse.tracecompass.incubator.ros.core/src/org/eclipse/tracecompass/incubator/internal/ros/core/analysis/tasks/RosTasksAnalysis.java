/**********************************************************************
 * Copyright (c) 2018 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.ros.core.analysis.tasks;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.ros.core.analysis.AbstractRosAnalysis;
import org.eclipse.tracecompass.incubator.internal.ros.core.trace.RosTrace;
import org.eclipse.tracecompass.incubator.internal.ros.core.trace.layout.IRosEventLayout;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement;

import com.google.common.collect.ImmutableSet;

/**
 * ROS Tasks analysis
 *
 * @author Christophe Bedard
 */
public class RosTasksAnalysis extends AbstractRosAnalysis {

    /** The ID suffix of this analysis module */
    public static final @NonNull String ID_SUFFIX = ".tasks"; //$NON-NLS-1$

    /**
     * Constructor
     */
    public RosTasksAnalysis() {
        super(getFullAnalysisId());
    }

    @Override
    protected Class<?> getRosAnalysisStateProviderClass() {
        return RosTasksStateProvider.class;
    }

    @Override
    public @NonNull Iterable<@NonNull TmfAbstractAnalysisRequirement> getAnalysisRequirements() {
        Set<@NonNull TmfAbstractAnalysisRequirement> requirements = fAnalysisRequirements;
        if (requirements == null) {
            RosTrace trace = getTrace();
            IRosEventLayout layout = IRosEventLayout.getDefault();
            if (trace != null) {
                layout = trace.getEventLayout();
            }
            requirements = ImmutableSet.of(new RosTasksAnalysisRequirement(checkNotNull(layout)));
            fAnalysisRequirements = requirements;
        }
        return requirements;
    }

    /**
     * @return the full ID of this analysis module
     */
    public static @NonNull String getFullAnalysisId() {
        return AbstractRosAnalysis.getAnalysisIdFromSuffix(ID_SUFFIX);
    }
}
