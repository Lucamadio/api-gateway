/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages periodic tasks with a single timer.
 */
public class TimerManager {
    protected final java.util.Timer timer = new Timer(true);

    public void schedulePeriodicTask(TimerTask task, long period, String title) {
        timer.schedule(task, period, period);
    }

    public void schedule(TimerTask task, long delay, String title) {
        timer.schedule(task, delay);
    }

    public void shutdown() {
        timer.cancel();
    }
}
