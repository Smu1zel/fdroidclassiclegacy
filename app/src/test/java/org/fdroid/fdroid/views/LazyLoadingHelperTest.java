package org.fdroid.fdroid.views;

import android.view.View;
import org.fdroid.fdroid.LazyLoadingHelper;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.*;
import java.util.function.Function;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class LazyLoadingHelperTest {
    private final String key1 = "key1";
    private final String key2 = "key2";
    private final View view = mock(View.class);
    private final Deque<Runnable> loadingTasks = new LinkedList<>();
    private final Deque<Runnable> bindingTasks = new LinkedList<>();
    private final LazyLoadingHelper.Callbacks<String, Integer> callbacks = mockCallbacks();
    private final InOrder orderedCalls = inOrder(callbacks);
    private final LazyLoadingHelper<String, Integer> helper =
            new LazyLoadingHelper<>(loadingTasks::add, bindingTasks::add, Function.identity());

    @Test
    public void loadsDataAndBindsView() {
        doReturn(123).when(callbacks).lazyLoadData(key1);

        helper.startLoading(view, key1, callbacks);
        runTasks(loadingTasks, bindingTasks);

        orderedCalls.verify(callbacks).lazyBindViewPlaceholder(view);
        orderedCalls.verify(callbacks).lazyLoadData(key1);
        orderedCalls.verify(callbacks).lazyBindView(view, 123);
        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    public void retargetsScheduledTask() {
        doReturn(123).when(callbacks).lazyLoadData(key1);
        doReturn(456).when(callbacks).lazyLoadData(key2);

        helper.startLoading(view, key1, callbacks);
        helper.startLoading(view, key2, callbacks);
        var taskCount = runTasks(loadingTasks);
        runTasks(bindingTasks);

        assertThat(taskCount).isEqualTo(1);
        orderedCalls.verify(callbacks, times(2)).lazyBindViewPlaceholder(view);
        orderedCalls.verify(callbacks, never()).lazyLoadData(key1);
        orderedCalls.verify(callbacks).lazyLoadData(key2);
        orderedCalls.verify(callbacks).lazyBindView(view, 456);
        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    public void cancelsScheduledTask() {
        doReturn(123).when(callbacks).lazyLoadData(key1);

        helper.startLoading(view, key1, callbacks);
        helper.cancelLoading(view);
        runTasks(loadingTasks, bindingTasks);

        orderedCalls.verify(callbacks).lazyBindViewPlaceholder(view);
        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    public void cancelsRunningTaskBeforeBinding() {
        doReturn(123).when(callbacks).lazyLoadData(key1);

        helper.startLoading(view, key1, callbacks);
        runTasks(loadingTasks);
        helper.cancelLoading(view);
        runTasks(bindingTasks);

        orderedCalls.verify(callbacks).lazyBindViewPlaceholder(view);
        orderedCalls.verify(callbacks).lazyLoadData(key1);
        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    public void cancelsRunningTaskBeforeBindingWhenRetargeted() {
        doReturn(123).when(callbacks).lazyLoadData(key1);
        doReturn(456).when(callbacks).lazyLoadData(key2);

        helper.startLoading(view, key1, callbacks);
        runTasks(loadingTasks);
        helper.startLoading(view, key2, callbacks);
        runTasks(loadingTasks);
        runTasks(bindingTasks);

        orderedCalls.verify(callbacks, never()).lazyBindView(view, 123);
        orderedCalls.verify(callbacks).lazyBindView(view, 456);
        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    public void skipsBindingPlaceholderWhenBindingSameKey() {
        doReturn(123, 456).when(callbacks).lazyLoadData(key1);

        helper.startLoading(view, key1, callbacks);
        runTasks(loadingTasks, bindingTasks);
        helper.startLoading(view, key1, callbacks);
        runTasks(loadingTasks, bindingTasks);

        orderedCalls.verify(callbacks).lazyBindViewPlaceholder(view);
        orderedCalls.verify(callbacks).lazyBindView(view, 123);
        orderedCalls.verify(callbacks, never()).lazyBindViewPlaceholder(view);
        orderedCalls.verify(callbacks).lazyBindView(view, 456);
        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    public void loadsDataOnTaskExecutor() {
        doReturn(123).when(callbacks).lazyLoadData(key1);

        helper.startLoading(view, key1, callbacks);
        verify(callbacks, never()).lazyLoadData(any());

        runTasks(loadingTasks);
        verify(callbacks).lazyLoadData(key1);
    }

    @Test
    public void bindsViewOnViewExecutor() {
        doReturn(123).when(callbacks).lazyLoadData(key1);

        helper.startLoading(view, key1, callbacks);
        verify(callbacks).lazyBindViewPlaceholder(view);

        runTasks(loadingTasks);
        verify(callbacks, never()).lazyBindView(any(), any());

        runTasks(bindingTasks);
        verify(callbacks).lazyBindView(view, 123);
    }

    @SafeVarargs
    private static int runTasks(Deque<Runnable>... taskQueues) {
        var totalTaskCount = 0;
        while (true) {
            var taskCount = 0;
            for (var queue : taskQueues) {
                if (!queue.isEmpty()) {
                    queue.removeFirst().run();
                    taskCount += 1;
                }
            }
            if (taskCount == 0) break;
            totalTaskCount += taskCount;
        }
        return totalTaskCount;
    }

    @SuppressWarnings("unchecked")
    private static <K, T> LazyLoadingHelper.Callbacks<K, T> mockCallbacks() {
        var callbacks = mock(LazyLoadingHelper.Callbacks.class);
        return (LazyLoadingHelper.Callbacks<K, T>) callbacks;
    }
}
