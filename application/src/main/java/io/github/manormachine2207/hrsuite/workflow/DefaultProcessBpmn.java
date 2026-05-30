package io.github.manormachine2207.hrsuite.workflow;

/**
 * Generates a minimal, valid BPMN 2.0 process (start → "review" user task → end) for
 * an antragstyp major. This stands in until the workflow-designer cut produces real
 * HR-authored BPMN: the antragstyp's stored {@code workflowBpmn} is a placeholder so
 * far, so {@link FlowableWorkflowEngine} substitutes this default when the stored XML
 * is not a deployable process. The BPMN {@code process id} is the process-definition key.
 */
public final class DefaultProcessBpmn {

    private DefaultProcessBpmn() {
    }

    /** True if the XML looks like a deployable BPMN process (not the placeholder). */
    public static boolean isDeployable(String bpmnXml) {
        return bpmnXml != null && bpmnXml.contains("<process");
    }

    public static String minimal(String processKey, String name) {
        String safeName = name == null ? processKey : name.replace("&", "&amp;").replace("<", "&lt;");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://hr-suite/processes">
                  <process id="%s" name="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="Review"/>
                    <sequenceFlow id="flow2" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(processKey, safeName);
    }
}
