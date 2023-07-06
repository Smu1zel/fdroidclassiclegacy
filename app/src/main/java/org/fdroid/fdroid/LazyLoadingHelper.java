/*
 * Copyright (C) 2023 Henrik Tunedal <tunedal@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.fdroid.fdroid;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.core.util.Pair;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Manages loading data on backgrounds threads and binding it to views.
 *
 * @param <K> type of item to load
 * @param <T> type of data object to pass from loading to binding
 */
public class LazyLoadingHelper<K, T> {
    private final Function<K, ?> keyTransform;
    private final Map<View, ViewDelegate> viewDelegates = new WeakHashMap<>();
    private final Function<View, ViewDelegate> viewDelegateFactory;

    /**
     * @param taskExecutor executor used for loading data
     * @param keyTransform maps key object to something implementing equals
     */
    public LazyLoadingHelper(Executor taskExecutor, Function<K, ?> keyTransform) {
        this(taskExecutor, new Handler(Looper.getMainLooper())::post, keyTransform);
    }

    /**
     * @param taskExecutor executor used for loading data
     * @param viewExecutor executor used for binding views
     * @param keyTransform maps key object to something implementing equals
     */
    public LazyLoadingHelper(Executor taskExecutor, Executor viewExecutor, Function<K, ?> keyTransform) {
        this.keyTransform = keyTransform;
        this.viewDelegateFactory = view -> new ViewDelegate(
                new WeakReference<>(view),
                new Worker(taskExecutor),
                new Worker(viewExecutor));
    }

    /**
     * Start loading an item in the background and, once loaded, bind it to the view.
     *
     * @param view the view to bind
     * @param key the item to load into the view
     * @param callbacks specify what to load and how to bind
     */
    public void startLoading(View view, K key, Callbacks<K, T> callbacks) {
        var viewDelegate = viewDelegates.computeIfAbsent(view, viewDelegateFactory);
        viewDelegate.startLoading(key, callbacks);
    }

    /**
     * Cancel all loading tasks for the given view.
     */
    public void cancelLoading(View view) {
        var viewDelegate = viewDelegates.get(view);
        if (viewDelegate != null) {
            viewDelegate.cancelLoading();
        }
    }

    public interface Callbacks<K, T> {
        /**
         * Executed on the {@code taskExecutor} provided in the constructor to load
         * the specified item. The return value will be passed to {@link #lazyBindView}.
         *
         * @param key the item to load
         * @return the loaded data
         */
        T lazyLoadData(K key);

        /**
         * Executed on the calling thread to bind placeholder values for the view
         * while waiting for the data to load.
         *
         * @param view the view to bind
         */
        void lazyBindViewPlaceholder(View view);

        /**
         * Executed on {@code viewExecutor} provided in the constructor to bind
         * the loaded data to the view.
         *
         * @param view the view to bind
         * @param data the loaded data
         */
        void lazyBindView(View view, T data);
    }

    /**
     * Handles loading and binding for a single view.
     */
    private class ViewDelegate {
        private final WeakReference<View> viewRef;
        private final Worker loadingWorker;
        private final Worker bindingWorker;

        private Object currentKey;
        private AtomicBoolean currentCancellationToken;

        public ViewDelegate(WeakReference<View> viewRef, Worker loadingWorker, Worker bindingWorker) {
            this.viewRef = viewRef;
            this.loadingWorker = loadingWorker;
            this.bindingWorker = bindingWorker;
            currentCancellationToken = new AtomicBoolean();
        }

        public void startLoading(K key, Callbacks<K, T> callbacks) {
            // Cancel any currently executing task.
            currentCancellationToken.set(true);

            // Set a new cancellation token for the new task.
            var cancellationToken = new AtomicBoolean();
            currentCancellationToken = cancellationToken;

            // Bind the placeholder immediately on the calling thread.
            bindViewPlaceholder(key, callbacks);

            // Schedule loading of the data in the background.
            loadingWorker.setNextTask(() -> loadData(key, callbacks, cancellationToken), cancellationToken);
        }

        public void cancelLoading() {
            currentCancellationToken.set(true);
        }

        private void loadData(K key, Callbacks<K, T> callbacks, AtomicBoolean cancellationToken) {
            // After loading the data, schedule the binding on the bindingExecutor.
            var data = callbacks.lazyLoadData(key);
            bindingWorker.setNextTask(() -> bindView(key, data, callbacks), cancellationToken);
        }

        private void bindViewPlaceholder(K key, Callbacks<K, T> callbacks) {
            // If an item with the same key is already bound to this view, skip binding
            // the placeholder while it's loading. This prevents blinking items during
            // frequent refresh.
            if (!Objects.equals(currentKey, keyTransform.apply(key))) {
                var view = viewRef.get();
                if (view != null) {
                    callbacks.lazyBindViewPlaceholder(view);
                }
            }
        }

        private void bindView(K key, T data, Callbacks<K, T> callbacks) {
            var view = viewRef.get();
            if (view != null && data != null) {
                callbacks.lazyBindView(view, data);
                currentKey = keyTransform.apply(key);
            }
        }
    }

    private static class Worker {
        private final Executor executor;
        private final AtomicReference<Pair<Runnable, AtomicBoolean>> nextTask = new AtomicReference<>();

        public Worker(Executor executor) {
            this.executor = executor;
        }

        public void setNextTask(Runnable command, AtomicBoolean cancellationToken) {
            // If nextTask was null, then there is no task enqueued on the wrapped executor,
            // either because we haven't submitted any tasks yet or because the submitted task
            // has already started executing, so we need to enqueue a new task.
            if (nextTask.getAndSet(Pair.create(command, cancellationToken)) == null) {
                executor.execute(this::runTask);
            }
        }

        private void runTask() {
            // Consume the enqueued task.
            var task = nextTask.getAndSet(null);

            if (task != null) {
                var command = task.first;
                var cancellationToken = task.second;

                // Run the command unless it's cancelled.
                if (!cancellationToken.get()) {
                    command.run();
                }
            }
        }
    }
}
