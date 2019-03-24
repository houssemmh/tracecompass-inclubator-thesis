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
//import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.event.*;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    private ArrayList<Long> stopped_threads = new ArrayList<>();
    private HashMap<Long, ThreadInfo> threads = new HashMap<>();
    //Pile pile = null;
    Map<Long, GC_struct> gcNames  = new HashMap<Long, GC_struct>() {
        private static final long serialVersionUID = -7630367047541779585L;

    {
        //new gen
        put((long) 3, new GC_struct("NewGen","ParallelScavenge"));//$NON-NLS-1$ //$NON-NLS-2$
        put((long) 6, new GC_struct("NewGen","G1New"));//$NON-NLS-1$ //$NON-NLS-2$
        put((long) 4, new GC_struct("NewGen","DefNew"));//$NON-NLS-1$ //$NON-NLS-2$
        put((long) 5, new GC_struct("NewGen","ParNew"));//$NON-NLS-1$ //$NON-NLS-2$
        //old gen
        put((long) 0, new GC_struct("OldGen","ParallelOld")); //$NON-NLS-1$ //$NON-NLS-2$
        put((long) 1, new GC_struct("OldGen","SerialOld")); //$NON-NLS-1$ //$NON-NLS-2$
        put((long) 2, new GC_struct("OldGen","PSMarkSweep")); //$NON-NLS-1$ //$NON-NLS-2$
        put((long) 7, new GC_struct("OldGen","ConcurrentMarkSweep"));//$NON-NLS-1$ //$NON-NLS-2$
        put((long) 8, new GC_struct("OldGen","G1Old"));//$NON-NLS-1$ //$NON-NLS-2$
    }};


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
        //threads.put(Long.valueOf(8644), new ThreadInfo(Long.valueOf(8641), "Main Thread", "JavaThreads"));
    }

    private static Long getVtid(ITmfEvent event) {
        /* We checked earlier that the "vtid" context is present */
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


    // private static String substringAfterLast(String str, String separator) {
    // int pos = str.lastIndexOf(separator);
    // if (pos == -1 || pos == (str.length() - separator.length())) {
    // return "";
    // }
    // return str.substring(pos + separator.length());
    // }

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
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            String threadtype;
            threadtype = "JavaThreads"; //$NON-NLS-1$
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, threadtype);
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, (Object) TmfStateValue.newValueLong(5), statusQuark);
            threads.put(tid, new ThreadInfo(pid, threadname, threadtype));
        }
            break;

        case "jvm:thread_stop": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            String threadtype;
            threadtype = "JavaThreads"; //$NON-NLS-1$
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
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
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
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
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
            int compilerthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "CompilerThreads");//$NON-NLS-1$
            int compilertypeQuark = ss.getQuarkRelativeAndAdd(compilerthreadsQuark, compilerName);
            int compilertidQuark = ss.getQuarkRelativeAndAdd(compilertypeQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(compilertidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(formatString(className) + ":"+ formatString(methodName)), statusQuark); //$NON-NLS-1$
        }
            break;
        case "jvm:method__compile__end": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String compilerName = event.getContent().getField("compilerName").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int compilerthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "CompilerThreads");//$NON-NLS-1$
            int compilertypeQuark = ss.getQuarkRelativeAndAdd(compilerthreadsQuark, compilerName);
            int compilertidQuark = ss.getQuarkRelativeAndAdd(compilertypeQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(compilertidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, statusQuark);
        }
            break;
        case "jvm:monitor__contended__enter": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            String monitorName = event.getContent().getField("monitorName").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JavaThreads");//$NON-NLS-1$
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
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JavaThreads");//$NON-NLS-1$
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
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JavaThreads");//$NON-NLS-1$
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
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JavaThreads");//$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int MonitornameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Monitor Name");//$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status");//$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, MonitornameQuark);
            ss.modifyAttribute(ts, (Object) TmfStateValue.newValueLong(5), statusQuark);
        }
            break;


        case "jvm:thread_status": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            if (stopped_threads.contains(tid)) {
                break;
            }
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int threadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "JavaThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            Long status = (Long) event.getContent().getField("status").getValue(); //$NON-NLS-1$
            if (status == 2) {
                ss.modifyAttribute(ts, (Object) null, statusQuark);
                stopped_threads.add(tid);
            } else {
                ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(status), statusQuark);
            }

            ThreadInfo pair = threads.get(tid);
            if (pair == null) {
                threads.put(tid, new ThreadInfo(pid, tid.toString(), "JavaThreads")); //$NON-NLS-1$
            }

        }
            break;
        case "jvm:vmthread_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, (Object) null, statusQuark);
            threads.put(tid, new ThreadInfo(pid, threadname, "VMThreads")); //$NON-NLS-1$
        }
            break;

        case "jvm:vmthread_stop": { //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            String targettid = event.getContent().getField("os_threadid").getValue().toString(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int vmthreadsQuark = ss.getQuarkRelativeAndAdd(pidQuark, "VMThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(vmthreadsQuark, targettid);
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, nameQuark);
            ss.modifyAttribute(ts, (Object) null, statusQuark);
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

        case "jvm:gctaskthread_start": { //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            Long targettid = (Long) event.getContent().getField("os_threadid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(gcQuark, "GCThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, targettid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String threadname = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int nameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(threadname), nameQuark);
            ss.modifyAttribute(ts, (Object) null, statusQuark);
            threads.put(targettid, new ThreadInfo(pid, threadname, "GCThreads")); //$NON-NLS-1$
        }
            break;
        case "jvm:report_gc_start": { //$NON-NLS-1$
            //Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            Long gdnameid = (Long) event.getContent().getField("name").getValue(); //$NON-NLS-1$
            GC_struct gc_struct = gcNames.get(gdnameid);
            String gctype = gc_struct.type;
            String gcname = gc_struct.name;
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
            int collectionsQuark = ss.getQuarkRelativeAndAdd(gcQuark, "Collections"); //$NON-NLS-1$
            int gctypequark = ss.getQuarkRelativeAndAdd(collectionsQuark, gctype);
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(gcname), gctypequark);
        }
            break;
        case "jvm:report_gc_end": { //$NON-NLS-1$
            //Long tid = (Long) event.getContent().getField("tid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("pid").getValue(); //$NON-NLS-1$
            Long gdnameid = (Long) event.getContent().getField("name").getValue(); //$NON-NLS-1$
            GC_struct gc_struct = gcNames.get(gdnameid);
            String gctype = gc_struct.type;
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
            int collectionsQuark = ss.getQuarkRelativeAndAdd(gcQuark, "Collections"); //$NON-NLS-1$
            int gctypequark = ss.getQuarkRelativeAndAdd(collectionsQuark, gctype);
            ss.modifyAttribute(ts, (Object) null, gctypequark);
        }
            break;

        case "jvm:gctask_start": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(gcQuark, "GCThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            String operationName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(operationName), infoQuark);
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(2), statusQuark);
        }
            break;
        case "jvm:gctask_end": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int gcQuark = ss.getQuarkRelativeAndAdd(pidQuark, "GC"); //$NON-NLS-1$
            int gcthreadsQuark = ss.getQuarkRelativeAndAdd(gcQuark, "GCThreads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(gcthreadsQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            int infoQuark = ss.getQuarkRelativeAndAdd(tidQuark, "info"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object) null, infoQuark);
            ss.modifyAttribute(ts, (Object) null, statusQuark);
        }
            break;

            /*
        case "jvm:notify":
        case "jvm:notifyAll": { //$NON-NLS-1$
            Long tid = getVtid(event);
            Long pid = (Long) event.getContent().getField("context._vpid").getValue();
            String monitorName = event.getContent().getField("name").getValue().toString(); //$NON-NLS-1$
            Long ptr = (Long) event.getContent().getField("ptr").getValue();
            int pidQuark = ss.getQuarkAbsoluteAndAdd(pid.toString());
            int contendedQuark = ss.getQuarkRelativeAndAdd(pidQuark, "Monitor");
            int monitorQuark = ss.getQuarkRelativeAndAdd(contendedQuark, ptr.toString());
            int nameQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "name"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(monitorName), nameQuark);
            int threadsQuark = ss.getQuarkRelativeAndAdd(monitorQuark, "threads"); //$NON-NLS-1$
            int tidQuark = ss.getQuarkRelativeAndAdd(threadsQuark, tid.toString());
            String threadName;
            ThreadInfo pair = threads.get(tid);
            if (pair != null) {
                threadName = pair.name;
            } else {
                threadName = tid.toString();
            }
            int threadNameQuark = ss.getQuarkRelativeAndAdd(tidQuark, "name"); //$NON-NLS-1$
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "User Status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(100), statusQuark);
            ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(threadName), threadNameQuark);
            ss.modifyAttribute(ts + 100, (Object) null, statusQuark);
        }
            break;
            */


        case "sched_switch": { //$NON-NLS-1$
            Long targettid = (Long) event.getContent().getField("next_tid").getValue(); //$NON-NLS-1$
            String targetcomm = event.getContent().getField("next_comm").getValue().toString(); //$NON-NLS-1$
            // This code is used to show the Kernel status under the user status; It can be replaced by accessing the kernel analysis
            /*
            Long prevtid = (Long) event.getContent().getField("prev_tid").getValue();
            ThreadInfo targetpair = threads.get(targettid);
            if (targetpair != null) {
                Long pid = targetpair.pid;
                try {
                    int pidQ = ss.getQuarkAbsolute(pid.toString());
                    int threadsQuark = ss.getQuarkRelative(pidQ, targetpair.type);
                    int tidQuark = ss.getQuarkRelative(threadsQuark, targettid.toString());
                    int kernelstatusQuark = ss.getQuarkRelative(tidQuark, "Kernel Status"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1001), kernelstatusQuark);
                } catch (AttributeNotFoundException e) {
                    e.printStackTrace();
                }
            }
            ThreadInfo prevpair = threads.get(prevtid);
            if (prevpair != null) {
                Long pid = prevpair.pid;
                try {
                    int pidQ = ss.getQuarkAbsolute(pid.toString());
                    int threadsQuark = ss.getQuarkRelative(pidQ, prevpair.type);
                    int tidQuark = ss.getQuarkRelative(threadsQuark, prevtid.toString());
                    int kernelstatusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "Kernel Status"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1002), kernelstatusQuark);
                } catch (AttributeNotFoundException e) {
                    e.printStackTrace();
                }
            }
*/
            int cpusQ = ss.getQuarkAbsoluteAndAdd("CPUs"); //$NON-NLS-1$
            int cpuQ = ss.getQuarkRelativeAndAdd(cpusQ, String.valueOf(cpu));
            int cpustatusQ = ss.getQuarkRelativeAndAdd(cpuQ, "status"); //$NON-NLS-1$
            int cpuinfoQ = ss.getQuarkRelativeAndAdd(cpuQ, "info"); //$NON-NLS-1$
            if (targettid != 0) {
                ThreadInfo target = threads.get(targettid);
                if (target != null) {
                    switch (target.type) {
                    case "JavaThreads": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1001), cpustatusQ);
                        break;
                    case "VMThreads": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1002), cpustatusQ);
                        break;
                    case "GCThreads": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1003), cpustatusQ);
                        break;
                    case "CompilerThreads": //$NON-NLS-1$
                        ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                        ss.modifyAttribute(ts + 1, (Object)TmfStateValue.newValueLong(1004), cpustatusQ);
                        break;
                    default:
                        break;
                    }
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(target.name + " (" + targettid.toString() + ")"), cpuinfoQ); //$NON-NLS-1$
                } else {
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1005), cpustatusQ);
                    ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(targetcomm + " (" + targettid.toString() + ")"), cpuinfoQ);
                }

            } else {
                ss.modifyAttribute(ts, (Object) null, cpustatusQ);
                ss.modifyAttribute(ts, (Object) null, cpuinfoQ);
            }

        }
            break;

            //This code is analysing IO requests, we can replace it be accessing the block IO analysis
            /*
        case "block_rq_insert": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._tid").getValue();
            Long disk = (Long) event.getContent().getField("dev").getValue();
            Long sector = (Long) event.getContent().getField("sector").getValue();
            Long size = (Long) event.getContent().getField("nr_sector").getValue();
            String processname = event.getContent().getField("comm").getValue().toString();
            if(disk.longValue()!=8388624) {
                break;
            }
            if(sector.longValue() <= 0 || size.longValue() <= 0) {
                break;
            }


            int ioQ = ss.getQuarkAbsoluteAndAdd("IO");
            int diskQ = ss.getQuarkRelativeAndAdd(ioQ, "sdb");
            if (pile ==null) {
                pile = new Pile(ss, diskQ);
            }

            ThreadInfo target = threads.get(tid);
            if (target != null) {
                pile.insert(sector, ts, size, processname,true);
            } else {
                pile.insert(sector, ts, size, processname,false);
            }
        }
            break;

        case "block_rq_complete": { //$NON-NLS-1$
            //Long tid = (Long) event.getContent().getField("context._tid").getValue();
            Long disk = (Long) event.getContent().getField("dev").getValue();
            Long sector = (Long) event.getContent().getField("sector").getValue();
            Long size = (Long) event.getContent().getField("nr_sector").getValue();
            if(disk.longValue()!=8388624) {
                break;
            }
            if(sector.longValue() <= 0 || size.longValue() <= 0) {
                break;
            }


            pile.remove(sector, ts);

        }
            break;
            */
        default:
            /* Ignore other event types */
            break;
        }
    }



//    /**
//     * @author houssemmh
//     *
//     */
//    public class Pile {
//        private Integer num_elements = 0; // Employee name
//        private HashMap<Long, Long> queue = new HashMap<>();
//        private HashMap<Long, Long> requests = new HashMap<>();
//        private ITmfStateSystemBuilder ss;
//        private int diskQ;
//
//        /**
//         * @param ss
//         *      statesystem
//         * @param diskQ
//         *      disk quark
//         *
//         */
//        public Pile(ITmfStateSystemBuilder ss, int diskQ) {
//            this.ss = ss;
//            this.diskQ = diskQ;
//        }
//
//        private Long findFirstAvailable() {
//            for (int i = 0; i < num_elements; i++) {
//                if (queue.get(Long.valueOf(i)) == null) {
//                    return Long.valueOf(i);
//                }
//            }
//            return Long.valueOf(0);
//        }
//
//        /**
//         * @param sector
//         *      sector
//         * @param ts
//         *      timestamp
//         * @param size
//         *      size
//         * @param processname
//         *      Process Name
//         * @param isjava
//         *      Is it a java thread
//         */
//        public void insert(Long sector, long ts, Long size, String processname, boolean isjava) {
//            if(sector.longValue()==738877456) {
//                return;
//            }
//            String name = processname;
//            if(name.equals("fio")) {//$NON-NLS-1$
//                name = "update.py";//$NON-NLS-1$
//            }
//            Long position = findFirstAvailable();
//            queue.put(position, sector);
//            requests.put(sector, position);
//            int slotQ = ss.getQuarkRelativeAndAdd(diskQ, String.format("%03d", position));//$NON-NLS-1$
//            int statusQ = ss.getQuarkRelativeAndAdd(slotQ, "status");//$NON-NLS-1$
//            int infoQ = ss.getQuarkRelativeAndAdd(slotQ, "info");//$NON-NLS-1$
//            if (isjava) {
//                ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1001), statusQ);
//                ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(name+" ("+String.valueOf(size/2)+"KB)"),  infoQ);//$NON-NLS-1$ //$NON-NLS-2$
//            } else {
//                ss.modifyAttribute(ts, (Object)TmfStateValue.newValueLong(1002), statusQ);
//                ss.modifyAttribute(ts, (Object)TmfStateValue.newValueString(name+" ("+String.valueOf(size/2)+"KB)"),  infoQ);//$NON-NLS-1$ //$NON-NLS-2$
//            }
//            num_elements++;
//        }
//
//        /**
//         * @param sector
//         *      sector
//         * @param ts
//         *      timestamp
//         */
//        public void remove(Long sector, long ts) {
//            Long position = requests.get(sector);
//                if (position != null) {
//                requests.put(position, null);
//                queue.put(position, null);
//                int slotQ = ss.getQuarkRelativeAndAdd(diskQ, String.format("%03d", position));//$NON-NLS-1$
//                int statusQ = ss.getQuarkRelativeAndAdd(slotQ, "status");//$NON-NLS-1$
//                int infoQ = ss.getQuarkRelativeAndAdd(slotQ, "info");//$NON-NLS-1$
//                ss.modifyAttribute(ts, (Object) null, statusQ);
//                ss.modifyAttribute(ts, (Object) null, infoQ);
//
//            }
//        }
//    }


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

    @Override
    public ITmfStateProvider getNewInstance() {
        return new JavaStateProvider(getTrace(), fLayout);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

}