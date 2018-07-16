/**********************************************************************
 * Copyright (c) 2018 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.ros.core.trace;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.ctf.core.CTFStrings;
import org.eclipse.tracecompass.ctf.core.event.IEventDeclaration;
import org.eclipse.tracecompass.ctf.core.event.IEventDefinition;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEventFactory;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;

/**
 * @author Christophe Bedard
 */
public class RosEventFactory extends CtfTmfEventFactory {

    private static final @NonNull RosEventFactory INSTANCE = new RosEventFactory();

    /**
     * Private constructor.
     */
    private RosEventFactory() {
        super();
    }

    public static @NonNull RosEventFactory instance() {
        return INSTANCE;
    }

    @Override
    public CtfTmfEvent createEvent(CtfTmfTrace trace, IEventDefinition eventDef, @Nullable String fileName) {
        /* Prepare what to pass to CtfTmfEvent's constructor */
        final IEventDeclaration eventDecl = eventDef.getDeclaration();
        final long ts = eventDef.getTimestamp();
        final ITmfTimestamp timestamp = trace.createTimestamp(trace.timestampCyclesToNanos(ts));

        int sourceCPU = eventDef.getCPU();

        String reference = (fileName == null ? NO_STREAM : fileName);

        /* Handle the special case of lost events */
        if (eventDecl.getName().equals(CTFStrings.LOST_EVENT_NAME)) {
            return createLostEvent(trace, eventDef, eventDecl, ts, timestamp, sourceCPU, reference);
        }

        /* Handle standard event types */
        return new RosEvent(trace,
                ITmfContext.UNKNOWN_RANK,
                timestamp,
                reference, // filename
                sourceCPU,
                eventDecl,
                eventDef);
    }
}
