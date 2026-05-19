package com.example.moderationapp;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.logic.moderation.ModerationTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModerationToolsGetReportsTests {
	private User userA;
	private User userB;
	private User userC;
	private Post post;
	private Post otherPost;
	private Message oldestReported;
	private Message mostReported;
	private Message laterReported;
	private Message unreported;
	private Message otherPostReported;

	@Before
	public void setUp() {
		// Build a deterministic fixture so ordering assertions are stable.

		UserDAO.getInstance().clear();
		PostDAO.getInstance().clear();
		ReportDAO.getInstance().clear();

		userA = new User(UUID.randomUUID(), User.Role.Member, "userA", "password");
		userB = new User(UUID.randomUUID(), User.Role.Member, "userB", "password");
		userC = new User(UUID.randomUUID(), User.Role.Member, "userC", "password");
		post = new Post(UUID.randomUUID(), userA.id(), "Reported messages");
		otherPost = new Post(UUID.randomUUID(), userB.id(), "Reports in another post");

		oldestReported = new Message(UUID.randomUUID(), userA.id(), post.id, 100L, "oldest report");
		mostReported = new Message(UUID.randomUUID(), userA.id(), post.id, 200L, "most reports");
		laterReported = new Message(UUID.randomUUID(), userA.id(), post.id, 300L, "later report");
		unreported = new Message(UUID.randomUUID(), userA.id(), post.id, 400L, "unreported");
		otherPostReported = new Message(UUID.randomUUID(), userB.id(), otherPost.id, 500L, "other post report");

		assertTrue(UserDAO.getInstance().add(userA));
		assertTrue(UserDAO.getInstance().add(userB));
		assertTrue(UserDAO.getInstance().add(userC));
		assertTrue(PostDAO.getInstance().add(post));
		assertTrue(PostDAO.getInstance().add(otherPost));
		assertTrue(post.messages.insert(oldestReported));
		assertTrue(post.messages.insert(mostReported));
		assertTrue(post.messages.insert(laterReported));
		assertTrue(post.messages.insert(unreported));
		assertTrue(otherPost.messages.insert(otherPostReported));
	}

	@After
	public void tearDown() {
		UserDAO.getInstance().clear();
		PostDAO.getInstance().clear();
		ReportDAO.getInstance().clear();
	}

	@Test(timeout = 1000)
	public void oldestStrategyOrdersByOldestActiveReportTimestamp() {
		// The oldest active report timestamp should decide OLDEST order.
		report(laterReported, userA, 30L);
		report(oldestReported, userA, 10L);
		report(mostReported, userA, 20L);
		report(mostReported, userB, 5L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 3));

		assertEquals(3, reported.size());
		assertMessageAt(reported, 0, mostReported);
		assertMessageAt(reported, 1, oldestReported);
		assertMessageAt(reported, 2, laterReported);
	}

	@Test(timeout = 1000)
	public void oldestStrategyIncludesReportedMessagesAcrossPosts() {
		// Reports are global across posts, not scoped to one thread.
		report(oldestReported, userA, 10L);
		report(otherPostReported, userB, 5L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 2));

		assertEquals(2, reported.size());
		assertMessageAt(reported, 0, otherPostReported);
		assertMessageAt(reported, 1, oldestReported);
	}

	@Test(timeout = 1000)
	public void oldestStrategyIgnoresRemovedReportsWhenChoosingOldestTimestamp() {
		// Removed reports must not affect the oldest active timestamp.
		report(oldestReported, userA, 1L);
		retract(oldestReported, userA);
		report(oldestReported, userB, 40L);
		report(laterReported, userA, 20L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 2));

		assertEquals(2, reported.size());
		assertMessageAt(reported, 0, laterReported);
		assertMessageAt(reported, 1, oldestReported);
	}

	@Test(timeout = 1000)
	public void oldestStrategyUsesReportTimestampNotMessageTimestamp() {
		// Message timestamps must not decide OLDEST order.
		report(oldestReported, userA, 50L);
		report(laterReported, userA, 5L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 2));

		assertEquals(2, reported.size());
		assertMessageAt(reported, 0, laterReported);
		assertMessageAt(reported, 1, oldestReported);
	}

	@Test(timeout = 1000)
	public void mostStrategyOrdersByActiveReportCount() {
		// MOST should rank messages by active report count.
		report(oldestReported, userA, 10L);
		report(mostReported, userA, 20L);
		report(mostReported, userB, 30L);
		report(laterReported, userA, 40L);
		report(laterReported, userB, 50L);
		report(laterReported, userC, 60L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("MOST", 3));

		assertEquals(3, reported.size());
		assertMessageAt(reported, 0, laterReported);
		assertMessageAt(reported, 1, mostReported);
		assertMessageAt(reported, 2, oldestReported);
	}

	@Test(timeout = 1000)
	public void mostStrategyCountsOnlyActiveReports() {
		// Removed reports must not contribute to the active count.
		report(mostReported, userA, 10L);
		report(mostReported, userB, 20L);
		retract(mostReported, userA);
		retract(mostReported, userB);
		report(mostReported, userC, 50L);
		report(laterReported, userA, 60L);
		report(laterReported, userB, 70L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("MOST", 2));

		assertEquals(2, reported.size());
		assertMessageAt(reported, 0, laterReported);
		assertMessageAt(reported, 1, mostReported);
	}

	@Test(timeout = 1000)
	public void mostStrategyReordersAfterHighCountMessageReportsAreRemoved() {
		// Ranking should update after reports are retracted.
		report(mostReported, userA, 10L);
		report(mostReported, userB, 20L);
		report(mostReported, userC, 30L);
		report(oldestReported, userA, 40L);
		report(oldestReported, userB, 50L);

		retract(mostReported, userA);
		retract(mostReported, userB);

		List<Message> reported = collect(ModerationTools.getReportedMessages("MOST", 2));

		assertEquals(2, reported.size());
		assertMessageAt(reported, 0, oldestReported);
		assertMessageAt(reported, 1, mostReported);
	}

	@Test(timeout = 1000)
	public void amountLimitsReturnedMessagesAndExcludesUnreportedMessages() {
		// The amount parameter limits results and unreported messages are excluded.
		report(oldestReported, userA, 10L);
		report(mostReported, userA, 20L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 1));

		assertEquals(1, reported.size());
		assertMessageAt(reported, 0, oldestReported);
		assertFalse(containsMessage(reported, unreported));
	}

	@Test(timeout = 1000)
	public void mostStrategyRespectsAmountLimit() {
		// The amount limit applies to MOST as well as OLDEST.
		report(oldestReported, userA, 10L);
		report(mostReported, userA, 20L);
		report(mostReported, userB, 30L);
		report(laterReported, userA, 40L);
		report(laterReported, userB, 50L);
		report(laterReported, userC, 60L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("MOST", 1));

		assertEquals(1, reported.size());
		assertMessageAt(reported, 0, laterReported);
		assertFalse(containsMessage(reported, mostReported));
		assertFalse(containsMessage(reported, oldestReported));
	}

	@Test(timeout = 1000)
	public void amountGreaterThanReportCountReturnsAllReportedMessages() {
		// Asking for too many messages should return only those available.
		report(oldestReported, userA, 10L);
		report(mostReported, userA, 20L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 10));

		assertEquals(2, reported.size());
		assertTrue(containsMessage(reported, oldestReported));
		assertTrue(containsMessage(reported, mostReported));
		assertFalse(containsMessage(reported, unreported));
	}

	@Test(timeout = 1000)
	public void amountEqualToReportCountReturnsAllReportedMessagesInOrder() {
		// Boundary case: amount exactly matches the number of reported messages.
		report(oldestReported, userA, 10L);
		report(mostReported, userA, 20L);
		report(laterReported, userA, 30L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 3));

		assertEquals(3, reported.size());
		assertMessageAt(reported, 0, oldestReported);
		assertMessageAt(reported, 1, mostReported);
		assertMessageAt(reported, 2, laterReported);
	}

	@Test(timeout = 1000)
	public void noActiveReportsReturnsEmptyIterator() {
		// With no active reports, no messages should be returned.
		List<Message> reported = collect(ModerationTools.getReportedMessages("MOST", 5));

		assertTrue(reported.isEmpty());
	}

	@Test(timeout = 1000)
	public void removedReportsAreNotReturned() {
		// A message with only removed reports has zero active reports.
		report(oldestReported, userA, 10L);
		retract(oldestReported, userA);

		List<Message> reported = collect(ModerationTools.getReportedMessages("OLDEST", 5));

		assertTrue(reported.isEmpty());
	}

	@Test(timeout = 1000)
	public void messageReportedMultipleTimesIsReturnedOnlyOnce() {
		// A reported message appears once even if several users reported it.
		report(mostReported, userA, 10L);
		report(mostReported, userB, 20L);
		report(mostReported, userC, 30L);

		List<Message> reported = collect(ModerationTools.getReportedMessages("MOST", 5));

		assertEquals(1, reported.size());
		assertMessageAt(reported, 0, mostReported);
	}

	@Test(timeout = 1000)
	public void returnedIteratorIsExhaustedAfterReportedMessagesAreConsumed() {
		// The returned iterator should expose exactly the requested results.
		report(oldestReported, userA, 10L);

		Iterator<Message> iterator = ModerationTools.getReportedMessages("OLDEST", 1);

		assertNotNull(iterator);
		assertTrue(iterator.hasNext());
		assertEquals(oldestReported.id(), iterator.next().id());
		assertFalse(iterator.hasNext());
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void invalidStrategyThrowsException() {
		ModerationTools.getReportedMessages("NEWEST", 1);
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void nullStrategyThrowsException() {
		ModerationTools.getReportedMessages(null, 1);
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void lowercaseStrategyThrowsException() {
		ModerationTools.getReportedMessages("oldest", 1);
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void zeroAmountThrowsException() {
		ModerationTools.getReportedMessages("OLDEST", 0);
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void negativeAmountThrowsException() {
		ModerationTools.getReportedMessages("MOST", -1);
	}

	private static List<Message> collect(Iterator<Message> iterator) {
		// Materialise the iterator so tests can assert size and order.
		List<Message> messages = new ArrayList<>();
		while (iterator.hasNext()) {
			messages.add(iterator.next());
		}
		return messages;
	}

	private static void assertMessageAt(List<Message> messages, int index, Message expected) {
		assertEquals(expected.id(), messages.get(index).id());
	}

	private static boolean containsMessage(List<Message> messages, Message expected) {
		for (Message message : messages) {
			if (message.id().equals(expected.id())) return true;
		}
		return false;
	}

	private static void report(Message message, User user, long timestamp) {
		// Use public moderation APIs to prepare black-box test state.
		ModerationTools.addReport(message.id(), user.id(), timestamp);
	}

	private static void retract(Message message, User user) {
		ModerationTools.removeReport(message.id(), user.id(), 0L);
	}
}