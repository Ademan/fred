package freenet.client.async;

import freenet.crypt.RandomSource;
import freenet.node.RequestStarter;
import freenet.support.RandomGrabArrayWithInt;
import freenet.support.SortedVectorByNumber;

/**
 * Every X seconds, the RequestSender calls the ClientRequestScheduler to
 * ask for a request to start. A request is then started, in its own 
 * thread. It is removed at that point.
 */
public class ClientRequestScheduler {

	/**
	 * Structure:
	 * array (by priority) -> // one element per possible priority
	 * SortedVectorByNumber (by # retries) -> // contains each current #retries
	 * RandomGrabArray // contains each element, allows fast fetch-and-drop-a-random-element
	 * 
	 * To speed up fetching, a RGA or SVBN must only exist if it is non-empty.
	 */
	final SortedVectorByNumber[] priorities;
	// we have one for inserts and one for requests
	final boolean isInsertScheduler;
	final RandomSource random;
	
	ClientRequestScheduler(boolean forInserts, RandomSource random) {
		this.random = random;
		this.isInsertScheduler = forInserts;
		priorities = new SortedVectorByNumber[RequestStarter.NUMBER_OF_PRIORITY_CLASSES];
		for(int i=0;i<priorities.length;i++)
			priorities[i] = new SortedVectorByNumber();
	}
	
	public synchronized void register(SendableRequest req) {
		if((!isInsertScheduler) && req instanceof ClientPut)
			throw new IllegalArgumentException("Expected a ClientPut: "+req);
		RandomGrabArrayWithInt grabber = 
			makeGrabArray(req.getPriorityClass(), req.getRetryCount());
		grabber.add(req);
	}
	
	private synchronized RandomGrabArrayWithInt makeGrabArray(short priorityClass, int retryCount) {
		SortedVectorByNumber prio = priorities[priorityClass];
		if(prio == null) {
			prio = new SortedVectorByNumber();
			priorities[priorityClass] = prio;
		}
		RandomGrabArrayWithInt grabber = (RandomGrabArrayWithInt) prio.get(retryCount);
		if(grabber == null) {
			grabber = new RandomGrabArrayWithInt(retryCount);
			prio.add(grabber);
		}
		return grabber;
	}

	/**
	 * Should not be called often as can be slow if there are many requests of the same
	 * priority and retry count. Priority and retry count must be the same as they were
	 * when it was added.
	 */
	public synchronized void remove(SendableRequest req) {
		// Should not be called often.
		int prio = req.getPriorityClass();
		int retryCount = req.getRetryCount();
		SortedVectorByNumber s = priorities[prio];
		if(s == null) return;
		if(s.isEmpty()) return;
		RandomGrabArrayWithInt grabber = 
			(RandomGrabArrayWithInt) s.get(retryCount);
		if(grabber == null) return;
		grabber.remove(req);
		if(grabber.isEmpty()) {
			s.remove(retryCount);
			if(s.isEmpty())
				priorities[prio] = null;
		}
	}
	
	public synchronized ClientRequest getFirst() {
		// Priorities start at 0
		for(int i=0;i<RequestStarter.MINIMUM_PRIORITY_CLASS;i++) {
			SortedVectorByNumber s = priorities[i];
			if(s == null) continue;
			RandomGrabArrayWithInt rga = (RandomGrabArrayWithInt) s.getFirst(); // will discard finished items
			ClientRequest req = (ClientRequest) rga.removeRandom();
			if(rga.isEmpty()) {
				s.remove(rga.getNumber());
				if(s.isEmpty()) {
					priorities[i] = null;
				}
			}
			return req;
		}
		return null;
	}
}
