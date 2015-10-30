/*
 * Copyright (c) 2010-2012 Grid Dynamics Consulting Services, Inc, All Rights Reserved
 * http://www.griddynamics.com
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.griddynamics.jagger.engine.e1.collector;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.griddynamics.jagger.coordinator.NodeContext;
import com.griddynamics.jagger.coordinator.NodeId;
import com.griddynamics.jagger.engine.e1.scenario.CalibrationInfo;
import com.griddynamics.jagger.storage.fs.logging.LogReader;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class ConsistencyValidatorTest {
    private ConsistencyValidator<Integer, Integer, Integer> consistencyValidator;
    private String sessionId;
    private String taskId;
    private NodeId nodeId;
    private LogReader logReader;
    private EquivalenceMock<Integer> queryEquivalence;
    private EquivalenceMock<Integer> endpointEquivalence;
    private EquivalenceMock<Integer> resultEquivalence;

    @BeforeMethod
    public void setUp() throws Exception {
        sessionId = "1";
        taskId = "task";
        nodeId = NodeId.kernelNode("1");
        logReader = mock(LogReader.class);
        queryEquivalence = new EquivalenceMock<>();
        endpointEquivalence = new EquivalenceMock<>();
        resultEquivalence = new EquivalenceMock<>();

        NodeContext kernelContext = mock(NodeContext.class);
        consistencyValidator = new ConsistencyValidator<Integer, Integer, Integer>(taskId, kernelContext, sessionId, queryEquivalence, endpointEquivalence, resultEquivalence);

        when(kernelContext.getService(LogReader.class)).thenReturn(logReader);
        when(kernelContext.getId()).thenReturn(nodeId);
    }

    @Test
    public void shouldValidateCorrectly() throws Exception {
        currentCalibrationInfo(CalibrationInfo.create(new Integer(1), new Integer(2), new Integer(3)));
        queryEquivalence.shouldBeEquals(1, 1);
        endpointEquivalence.shouldBeEquals(2, 2);
        resultEquivalence.shouldBeEquals(3, 3);

        boolean validate = consistencyValidator.validate(1, 2, 3, 10L);

        assertThat(validate, is(true));
        queryEquivalence.verifyInvokation(1, 1);
        endpointEquivalence.verifyInvokation(2, 2);
        resultEquivalence.verifyInvokation(3, 3);
    }

    @Test
    public void shouldFailBecauseResultDoesNotMatch() throws Exception {
        currentCalibrationInfo(CalibrationInfo.create(new Integer(1), new Integer(2), new Integer(3)));
        queryEquivalence.shouldBeEquals(1, 1);
        endpointEquivalence.shouldBeEquals(2, 2);
        resultEquivalence.shouldBeFifferent(3, 3);

        boolean validate = consistencyValidator.validate(1, 2, 3, 10L);

        assertThat(validate, is(false));
        queryEquivalence.verifyInvokation(1, 1);
        endpointEquivalence.verifyInvokation(2, 2);
        resultEquivalence.verifyInvokation(3, 3);
    }

    @Test
    public void shouldFailBecauseQueryDoesNotMatch() throws Exception {
        currentCalibrationInfo(CalibrationInfo.create(new Integer(1), new Integer(2), new Integer(3)));
        queryEquivalence.shouldBeFifferent(1, 1);
        endpointEquivalence.shouldBeEquals(2, 2);

        boolean validate = consistencyValidator.validate(1, 2, 3, 10L);

        assertThat(validate, is(false));
        queryEquivalence.verifyInvokation(1, 1);
        resultEquivalence.verifyNotInvoked();
    }

    @Test
    public void shouldFailBecauseEndpointDoesNotMatch() throws Exception {
        currentCalibrationInfo(CalibrationInfo.create(new Integer(1), new Integer(2), new Integer(3)));
        queryEquivalence.shouldBeEquals(1, 1);
        endpointEquivalence.shouldBeFifferent(2, 2);

        boolean validate = consistencyValidator.validate(1, 2, 3, 10L);

        assertThat(validate, is(false));
        endpointEquivalence.verifyInvokation(2, 2);
        resultEquivalence.verifyNotInvoked();
    }

    private void currentCalibrationInfo(CalibrationInfo... elements) {
        ImmutableList<CalibrationInfo> list = ImmutableList.copyOf(elements);

        LogReader.FileReader<CalibrationInfo> result = mock(LogReader.FileReader.class);
        when(result.iterator()).thenReturn(list.iterator());
        when(logReader.read(sessionId, taskId + "/Calibration", "kernel", CalibrationInfo.class)).thenReturn(result);
    }

    private static class EquivalenceMock<T> extends Equivalence<T> {

        private Map<T, Set<T>> equals = new HashMap<>();
        private Map<T, Set<T>> invokations = new HashMap<>();

        public void shouldBeEquals(T a, T b) {
            equals.putIfAbsent(a, new HashSet<>());
            Set<T> set = equals.get(a);
            set.add(b);
        }

        public void shouldBeFifferent(T a, T b) {
            equals.putIfAbsent(a, new HashSet<>());
            Set<T> set = equals.get(a);
            set.remove(b);
        }

        public void verifyInvokation(T a, T b) {
            invokations.putIfAbsent(a, new HashSet<>());
            Set<T> set = invokations.get(a);
            if (!set.contains(b)) {
                throw new IllegalStateException("Verified but not invoked " + a + ".equals(" + b + ")");
            }
        }

        public void verifyNotInvoked() {
            if (!invokations.isEmpty()) {
                throw new IllegalStateException("Not wanted but invoked " + invokations);
            }
        }

        @Override
        protected boolean doEquivalent(T a, T b) {
            final Set<T> set = equals.getOrDefault(a, new HashSet<>());
            invokations.putIfAbsent(a, new HashSet<>());
            invokations.get(a).add(b);
            return set.contains(b);
        }

        @Override
        protected int doHash(T t) {
            return t.hashCode();
        }
    }
}
