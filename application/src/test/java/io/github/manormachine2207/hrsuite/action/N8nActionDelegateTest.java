package io.github.manormachine2207.hrsuite.action;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class N8nActionDelegateTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock
    private ActionExecutionService service;
    @Mock
    private DelegateExecution execution;
    @Mock
    private Expression refExpression;

    private N8nActionDelegate delegate() {
        N8nActionDelegate d = new N8nActionDelegate(service);
        d.setRef(refExpression);
        return d;
    }

    private void stubExecution() {
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        when(execution.getCurrentActivityId()).thenReturn("ad");
        when(refExpression.getValue(execution)).thenReturn("provision-ad-account");
        lenient().when(execution.getVariable("actionInput")).thenReturn(Map.of("upn", "a@b.ch"));
    }

    @Test
    void runsActionAndSetsStatusOnSuccess() {
        stubExecution();
        ActionExecution succeeded = new ActionExecution(TENANT, "pi-1", "ad", "provision-ad-account");
        succeeded.markSucceeded();
        when(service.run(eq("pi-1"), eq("ad"), eq("provision-ad-account"), any())).thenReturn(succeeded);

        delegate().execute(execution);

        verify(execution).setVariable("actionStatus", "SUCCEEDED");
    }

    @Test
    void throwsBpmnErrorWhenDead() {
        stubExecution();
        ActionExecution dead = new ActionExecution(TENANT, "pi-1", "ad", "provision-ad-account");
        dead.markDead("boom");
        when(service.run(any(), any(), any(), any())).thenReturn(dead);

        assertThatThrownBy(() -> delegate().execute(execution))
                .isInstanceOf(BpmnError.class);
    }

    @Test
    void inputMappingJsonFieldIsUsedWhenActionInputVariableAbsent() {
        stubExecution();
        when(execution.getVariable("actionInput")).thenReturn(null);   // no process variable
        Expression mappingExpr = mock(Expression.class);
        when(mappingExpr.getValue(execution)).thenReturn("{\"upn\":\"a@b.ch\"}");

        ActionExecution succeeded = new ActionExecution(TENANT, "pi-1", "ad", "provision-ad-account");
        succeeded.markSucceeded();
        when(service.run(eq("pi-1"), eq("ad"), eq("provision-ad-account"), any())).thenReturn(succeeded);

        N8nActionDelegate d = delegate();
        d.setInputMappingJson(mappingExpr);
        d.execute(execution);

        verify(execution).setVariable("actionStatus", "SUCCEEDED");

        // Verify the parsed map was actually forwarded to the service (not an empty map)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(service).run(eq("pi-1"), eq("ad"), eq("provision-ad-account"), inputCaptor.capture());
        assertThat(inputCaptor.getValue()).containsExactlyEntriesOf(Map.of("upn", "a@b.ch"));
    }
}
