/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.java.analysis.core.stateprovider;


import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.event.*;
import java.lang.String;
import java.util.HashMap;

/**
 * @author Houssem Daoud
 */
public class JavaStateProvider extends AbstractTmfStateProvider {

    /* Version of this state provider */
    class ThreadInfo {
        public Long pid;
        public String name;
        public String type;

        public ThreadInfo(Long pid, String name, String type) {
            this.pid = pid;
            this.name = name;
            this.type = type;
        }
    }

    class GC_struct {
        public String name;
        public String type;

        public GC_struct(String type, String name) {
            this.name = name;
            this.type = type;
        }
    }

    private static final int VERSION = 1;
    private static final Long MINUS_ONE = Long.valueOf(-1);
    private final @NonNull IKernelAnalysisEventLayout fLayout;
    private HashMap<Long, ThreadInfo> threads = new HashMap<>();

    /**
     * Constructor
     *
     * @param trace
     *            trace
     * @param layout
     *            layout
     */
    @SuppressWarnings("null")
    public JavaStateProvider(@NonNull ITmfTrace trace, IKernelAnalysisEventLayout layout) {
        super(trace, "Ust:Java"); //$NON-NLS-1$
        fLayout = layout;
    }

    private static Long getVtid(ITmfEvent event) {
        ITmfEventField field = event.getContent().getField("context._vtid"); //$NON-NLS-1$
        if (field == null) {
            return MINUS_ONE;
        }
        return (Long) field.getValue();
    }

     private static int getCpu(ITmfEvent event) {
        // We checked earlier that the "vtid" context is present
        return ((CtfTmfEvent) event).getCPU();
    }

    @Override
    protected void eventHandle(ITmfEvent event) {
        String name = event.getName();
        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
        long ts = event.getTimestamp().toNanos();
        int cpu = getCpu(event);

        switch (name) {
        case "jvm:thread_start": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            String threadtype;
            threadtype = getThreadType(threadname);
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, threadtype);
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, (Object) TmfStateValue.newValueLong(5), statusQuark);
            threads.put(tid, new ThreadInfo(pid, threadname, threadtype));
        }
            break;

        case "jvm:statedump_java_thread": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String threadname = event.getContent().getField("threadName").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            String threadtype;
            threadtype = getThreadType(threadname);
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, threadtype);
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, (Object) null, statusQuark);
            threads.put(tid, new ThreadInfo(pid, threadname, threadtype));
        }
            break;

        case "jvm:thread_stop": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            String threadtype;
            threadtype = "Threads"; //$NON-NLS-1$
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, threadtype);
            int tidQuark;
            try {
                tidQuark = ss.getQuarkRelative(threadsQuark, tid.toString());
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, (Object) null, statusQuark);
            } catch (AttributeNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
            break;

        case "jvm:mem__pool__gc__begin": { //$NON-NLS-1$
            //Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String gcname = event.getContent().getField("gc_name").getValue().toString(); //$NON-NLS-1$
            String poolname = event.getContent().getField("pool_name").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Garbage Collection"); //$NON-NLS-1$
            int gcnameQuark = ss.getQuarkRelativeAndAdd(gcQuark, gcname);
            int poolquark = ss.getQuarkRelativeAndAdd(gcnameQuark, poolname);
            ss.modifyAttribute(ts, (Object) TmfStateValue.newValueLong(2), poolquark);
        }
            break;
        case "jvm:mem__pool__gc__end": { //$NON-NLS-1$
            //Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String gcname = event.getContent().getField("gc_name").getValue().toString(); //$NON-NLS-1$
            String poolname = event.getContent().getField("pool_name").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Garbage Collection"); //$NON-NLS-1$
            int gcnameQuark = ss.getQuarkRelativeAndAdd(gcQuark, gcname);
            int poolquark = ss.getQuarkRelativeAndAdd(gcnameQuark, poolname);
            ss.modifyAttribute(ts, (Object) null , poolquark);
        }
            break;

        case "jvm:method__compile__begin": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String compilerName = event.getContent().getField("compilerName").getValue().toString(); //$NON-NLS-1$
            String className = event.getContent().getField("className").getValue().toString(); //$NON-NLS-1$
            String methodName = event.getContent().getField("methodName").getValue().toString(); //$NON-NLS-1$
            //String signature = event.getContent().getField("signature").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int compilerthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JIT Compilation");//$NON-NLS-1$
            int compilertypeQuark = ss.getQuarkRelativeAndAdd(compilerthreadsQuark, compilerName);
            String thread_name_or_tid = get_thread_name_or_tid(tid);
            int compilertidQuark = ss.getQuarkRelativeAndAdd(compilertypeQuark, thread_name_or_tid);
            int statusQuark = ss.getQuarkRelativeAndAdd(compilertidQuark, "status"); //$NON-NLS-1$
            int functionNameQuark = ss.getQuarkRelativeAndAdd(compilertidQuark, "functionName"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(formatString(className) + ":"+ formatString(methodName)), functionNameQuark); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(3), statusQuark);
        }
            break;
        case "jvm:method__compile__end": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String compilerName = event.getContent().getField("compilerName").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int compilerthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JIT Compilation");//$NON-NLS-1$
            int compilertypeQuark = ss.getQuarkRelativeAndAdd(compilerthreadsQuark, compilerName);
            String thread_name_or_tid = get_thread_name_or_tid(tid);
            int compilertidQuark = ss.getQuarkRelativeAndAdd(compilertypeQuark, thread_name_or_tid);
            int statusQuark = ss.getQuarkRelativeAndAdd(compilertidQuark, "status"); //$NON-NLS-1$
            int functionNameQuark = ss.getQuarkRelativeAndAdd(compilertidQuark, "functionName"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, statusQuark);
            ss.modifyAttribute(ts, (Object) null, functionNameQuark);
        }
            break;
        case "jvm:monitor__contended__enter": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String monitorName = event.getContent().getField("monitorName").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Threads");//$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int MonitornameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Monitor Name");//$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status");//$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(formatString(monitorName)), MonitornameQuark);
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(6), statusQuark);
        }
            break;
        case "jvm:monitor__contended__entered": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Threads");//$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int MonitornameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Monitor Name");//$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status");//$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, MonitornameQuark);
            ss.modifyAttribute(ts, (Object) TmfStateValue.newValueLong(5),  statusQuark);
        }
            break;
        case "jvm:monitor__wait": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String monitorName = event.getContent().getField("monitorName").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Threads");//$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int MonitornameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Monitor Name");//$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status");//$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(formatString(monitorName)), MonitornameQuark);
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(4), statusQuark);
        }
            break;
        case "jvm:monitor__waited": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Threads");//$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int MonitornameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Monitor Name");//$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status");//$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, MonitornameQuark);
            ss.modifyAttribute(ts, (Object) TmfStateValue.newValueLong(5), statusQuark);
        }
            break;

        case "jvm:vmops_begin": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String operationName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(operationName), infoQuark);
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1), statusQuark);
        }
            break;
        case "jvm:vmops_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, infoQuark);
            ss.modifyAttribute(ts, (Object) null, statusQuark);
        }
            break;

        case "sched_switch": { //$NON-NLS-1$
            Long targettid = (Long) event.getContent().getField("next_tid").getValue(); //$NON-NLS-1$
            String targetcomm = event.getContent().getField("next_comm").getValue().toString(); //$NON-NLS-1$

            int cpusQ = ss.getQuarkAbsoluteAndAdd("CPUs"); //$NON-NLS-1$
            int cpuQ = ss.getQuarkRelativeAndAdd(cpusQ, String.valueOf(cpu));
            int cpustatusQ = ss.getQuarkRelativeAndAdd(cpuQ, "status"); //$NON-NLS-1$
            int cpuinfoQ = ss.getQuarkRelativeAndAdd(cpuQ, "info"); //$NON-NLS-1$
            if (targettid != 0) {
                ThreadInfo target = threads.get(targettid);
                if (target != null) {
                    switch (target.type) {
                    case "Threads": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1001), cpustatusQ);
                        break;
                    case "VMThreads": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1002), cpustatusQ);
                        break;
                    case "GCThread": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1003), cpustatusQ);
                        break;
                    case "CompilerThread": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1004), cpustatusQ);
                        break;
                    default:
                        break;
                    }
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(target.name + " (" + targettid.toString() + ")"), cpuinfoQ); //$NON-NLS-1$ //$NON-NLS-2$
                } else {
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1005), cpustatusQ);
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(targetcomm + " (" + targettid.toString() + ")"), cpuinfoQ); //$NON-NLS-1$ //$NON-NLS-2$
                }

            } else {
                ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                ss.modifyAttribute(ts, (Object) null, cpuinfoQ);
            }

        }
            break;

        default:
            /* Ignore other event types */
            break;
        }
    }

    /**
     * @param strValue
     *  string
     * @return
     *  string
     */

    private static String formatString(String text)
    {
        String mytxt;
        mytxt = text;
        // strips off all non-ASCII characters
        mytxt = mytxt.replaceAll("[^a-zA-Z0-9\\/]", ""); //$NON-NLS-1$ //$NON-NLS-2$

        return mytxt.trim();
    }

    private  String get_thread_name_or_tid(Long tid)
    {
        String name_or_tid;
        ThreadInfo pair = threads.get(tid);
        if (pair != null) {
            name_or_tid = pair.name;
        } else {
            name_or_tid = tid.toString();
        }
        return name_or_tid;
    }

    private static String getThreadType(String name)
    {
        if (name.contains("CompilerThread") ) { //$NON-NLS-1$
            return "CompilerThread"; //$NON-NLS-1$
        } else if (name.contains("GC task thread") ) { //$NON-NLS-1$
            return "GCThread"; //$NON-NLS-1$
        } else {
            return "Threads"; //$NON-NLS-1$
        }

    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new JavaStateProvider(getTrace(), fLayout);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

}