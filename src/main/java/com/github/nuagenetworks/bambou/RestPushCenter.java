/**
 * Copyright (c) 2016, Nokia Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the copyright holder nor the names of its contributors
 *       may be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.package com.github.nuagenetworks.bambou.operation;
 */
package com.github.nuagenetworks.bambou;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.nuagenetworks.bambou.model.Events;

public class RestPushCenter {
	
	private static final Logger logger = LoggerFactory.getLogger(RestPushCenter.class);
	
	private String url;
	private List<RestPushCenterListener> listeners = new ArrayList<RestPushCenterListener>();
	private Thread eventThread;
	private boolean stopPollingEvents;
	private boolean isRunning;
	private RestSession<?> session;

	protected RestPushCenter(RestSession<?> session) {
		this.session = session;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void start() {
		if (isRunning) {
			return;
		}

		isRunning = true;
		Runnable exec = new Runnable() {
			public void run() {
				pollEvents();
			}
		};
		eventThread = new Thread(exec);
		eventThread.start();
	}

	public void stop() {
		if (!isRunning) {
			return;
		}

		stopPollingEvents = true;
		try {
			eventThread.join();
		} catch (InterruptedException e) {
		}
		isRunning = false;
		eventThread = null;

	}

	public void addListener(RestPushCenterListener listener) {
		if (listeners.contains(listener)) {
			return;
		}

		listeners.add(listener);
	}

	public void removeListener(RestPushCenterListener listener) {
		if (!listeners.contains(listener)) {
			return;
		}

		listeners.remove(listener);
	}

	private void pollEvents() {
		stopPollingEvents = false;

		String uuid = null;
		while (!stopPollingEvents) {
			try {
				// Debug
				logger.debug("Polling events from VSD using uuid=" + uuid);

				// Get the next events
				ResponseEntity<Events> response = sendRequest(uuid);
				Events events = (Events) response.getBody();

				if (stopPollingEvents) {
					break;
				}

				// Debug
				logger.debug("Received events: " + events);

				// Process the events received
				for (JsonNode event : events.getEvents()) {
					for (RestPushCenterListener listener : listeners) {
						listener.onEvent(event);
					}
				}

				// Get the next UUID to query for
				uuid = events.getUuid();
			} catch (Exception ex) {
				// Error
				logger.error("Error", ex);

				// Pause and try again
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
				}
			}
		}

		// Debug
		logger.debug("Polling stopped");
	}

	private ResponseEntity<Events> sendRequest(String uuid) throws RestException {
		String eventsUrl = String.format("%s/events", url);
		String params = (uuid != null) ? "uuid=" + uuid : null;

		ResponseEntity<Events> response = session.sendRequestWithRetry(HttpMethod.GET, eventsUrl, params, null, null, Events.class);
		if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
			// In case of a 400/Bad Request: re-send request without uuid in order to get a new one
			response = sendRequest(null);
		}

		return response;
	}
}
