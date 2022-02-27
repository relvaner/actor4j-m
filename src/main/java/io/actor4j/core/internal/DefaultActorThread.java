/*
 * Copyright (c) 2015-2019, David A. Bauer. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.actor4j.core.internal;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import io.actor4j.core.messages.ActorMessage;

public abstract class DefaultActorThread extends ActorThread {
	protected Queue<ActorMessage<?>> directiveQueue;
	protected Queue<ActorMessage<?>> priorityQueue;
	protected Queue<ActorMessage<?>> innerQueue;
	protected Queue<ActorMessage<?>> outerQueueL2;
	protected Queue<ActorMessage<?>> outerQueueL1;
	protected Queue<ActorMessage<?>> serverQueueL2;
	protected Queue<ActorMessage<?>> serverQueueL1;
	
	protected final AtomicBoolean newMessage;
	
	public DefaultActorThread(ThreadGroup group, String name, ActorSystemImpl system) {
		super(group, name, system);
		
		configQueues();
		
		newMessage = new AtomicBoolean(true);
	}
	
	
	public abstract void configQueues();
	
	@Override
	public void directiveQueue(ActorMessage<?> message) {
		directiveQueue.offer(message);
	}
	
	@Override
	public void priorityQueue(ActorMessage<?> message) {
		priorityQueue.offer(message);
	}
	
	@Override
	public void serverQueue(ActorMessage<?> message) {
		serverQueueL2.offer(message);
	}
	
	@Override
	public void outerQueue(ActorMessage<?> message) {
		outerQueueL2.offer(message);
	}
	
	@Override
	public void innerQueue(ActorMessage<?> message) {
		innerQueue.offer(message);
	}
	
	@Override
	public void onRun() {
		boolean hasNextDirective;
		boolean hasNextPriority;
		int hasNextServer;
		int hasNextOuter;
		int hasNextInner;
		int idle = 0;
		int load = 0;
		
		while (!isInterrupted()) {
			hasNextDirective = false;
			hasNextPriority  = false;
			hasNextServer    = 0;
			hasNextOuter     = 0;
			hasNextInner     = 0;
			
			while (poll(directiveQueue)) 
				hasNextDirective=true;
			
			while (poll(priorityQueue)) 
				hasNextPriority=true;
			
			if (system.config.serverMode) {
				for (; hasNextServer<system.config.throughput && poll(serverQueueL1); hasNextServer++);
				if (hasNextServer<system.config.throughput && serverQueueL2.peek()!=null) {
					ActorMessage<?> message = null;
					for (int j=0; j<system.config.bufferQueueSize && (message=serverQueueL2.poll())!=null; j++)
						serverQueueL1.offer(message);
				
					for (; hasNextServer<system.config.throughput && poll(serverQueueL1); hasNextServer++);
				}
			}
			
			for (; hasNextOuter<system.config.throughput && poll(outerQueueL1); hasNextOuter++);
			if (hasNextOuter<system.config.throughput && outerQueueL2.peek()!=null) {
				ActorMessage<?> message = null;
				for (int j=0; j<system.config.bufferQueueSize && (message=outerQueueL2.poll())!=null; j++)
					outerQueueL1.offer(message);

				for (; hasNextOuter<system.config.throughput && poll(outerQueueL1); hasNextOuter++);
			}
			
			for (; hasNextInner<system.config.throughput && poll(innerQueue); hasNextInner++);
			
			if (hasNextInner==0 && hasNextOuter==0 && hasNextServer==0 && !hasNextPriority && !hasNextDirective) {
				if (idle>system.config.load) {
					load = 0;
					threadLoad.set(false);
				}
				idle++;
				if (idle>system.config.idle) {
					idle = 0;
					if (system.config.threadMode==ActorThreadMode.PARK) {
						if (newMessage.compareAndSet(true, false))
							LockSupport.park(this);
					}
					else if (system.config.threadMode==ActorThreadMode.SLEEP) {
						try {
							sleep(system.config.sleepTime);
						} catch (InterruptedException e) {
							interrupt();
						}
					}
					else
						Thread.yield();
				}
			}
			else {
				idle = 0;
				if (load>system.config.load)
					threadLoad.set(true);
				else
					load++;
			}
		}		
	}
	
	@Override
	protected void newMessage() {
		if (system.config.threadMode==ActorThreadMode.PARK && newMessage.compareAndSet(false, true))
			LockSupport.unpark(this);
	}
	
	@Override
	public Queue<ActorMessage<?>> getDirectiveQueue() {
		return directiveQueue;
	}
	
	@Override
	public Queue<ActorMessage<?>> getPriorityQueue() {
		return priorityQueue;
	}
	
	@Override
	public Queue<ActorMessage<?>> getServerQueue() {
		return serverQueueL2;
	}
	
	@Override
	public Queue<ActorMessage<?>> getOuterQueue() {
		return outerQueueL2;
	}

	@Override
	public Queue<ActorMessage<?>> getInnerQueue() {
		return innerQueue;
	}
}
