/*
 * Copyright (c) 2015, David A. Bauer
 */
package actor4j.core;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import org.jctools.queues.MpscArrayQueue;

import actor4j.core.actors.Actor;
import actor4j.core.exceptions.ActorInitializationException;
import actor4j.core.messages.ActorMessage;
import actor4j.core.utils.ActorFactory;
import actor4j.core.utils.ActorMessageObservable;
import rx.Observable;
import safety4j.SafetyManager;

public class PseudoActorCell extends ActorCell {
	protected Queue<ActorMessage<?>> outerQueueL2;
	protected Queue<ActorMessage<?>> outerQueueL1;
	
	protected Observable<ActorMessage<?>> rxOuterQueueL1;
	
	public PseudoActorCell(ActorSystem wrapper, Actor actor) {
		super(wrapper.system, actor);
		
		outerQueueL2 = new MpscArrayQueue<>(50000);
		outerQueueL1 = new LinkedList<>();
		
		rxOuterQueueL1 = ActorMessageObservable.getMessages(outerQueueL1);
	}
	
	public UUID system_addCell(ActorCell cell) {
		return system.system_addCell(cell);
	}

	protected void safetyMethod(ActorMessage<?> message) {
		try {
			internal_receive(message);
		}
		catch(Exception e) {
			SafetyManager.getInstance().notifyErrorHandler(e, "pseudo", id);
		}	
	}
	
	protected boolean poll(Queue<ActorMessage<?>> queue) {
		boolean result = false;
		
		ActorMessage<?> message = queue.poll();
		if (message!=null) {
			safetyMethod(message);
			result = true;
		} 
		
		return result;
	}
	
	public void run() {
		boolean hasNextOuter = outerQueueL1.peek()!=null;
		if (!hasNextOuter && outerQueueL2.peek()!=null) {
			ActorMessage<?> message = null;
			for (int j=0; (message=outerQueueL2.poll())!=null && j<10000; j++)
				outerQueueL1.offer(message);
		}
		while (poll(outerQueueL1));
	}
	
	public Observable<ActorMessage<?>> runWithRx() {
		boolean hasNextOuter = outerQueueL1.peek()!=null;
		if (!hasNextOuter && outerQueueL2.peek()!=null) {
			ActorMessage<?> message = null;
			for (int j=0; (message=outerQueueL2.poll())!=null && j<10000; j++)
				outerQueueL1.offer(message);
		}
		
		return rxOuterQueueL1;
	}
	
	@Override
	public void send(ActorMessage<?> message) {
		system.send(message);
	}
	
	@Override
	public void send(ActorMessage<?> message, String alias) {
		system.send(message);
	}
	
	@Override
	public UUID internal_addChild(ActorCell cell) {
		return null;
	}
	
	@Override
	public UUID addChild(Class<? extends Actor> clazz, Object... args) throws ActorInitializationException {
		return null;
	}
	
	@Override
	public UUID addChild(ActorFactory factory) {
		return null;
	}
}
