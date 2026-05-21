package com.example.moderationapp;

import com.example.moderationapp.data.dao.MessageComparator;
import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.Report;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.data.persistence.DataManager;
import com.example.moderationapp.data.persistence.DataPipeline;
import com.example.moderationapp.data.persistence.serialization.MessageSerializer;
import com.example.moderationapp.data.persistence.serialization.ReportSerializer;
import com.example.moderationapp.logic.moderation.ModerationTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * White-box tests for Task 5's addReport requirement.
 * The main checks cover each valid and invalid branch of addReport.
 *
 */
public class ModerationToolsAddReportTests {
	private User reporter;
	private User otherReporter;
	private Post post;
	private Message message;

	@Before
	public void setUp() {
		// Each test starts from a clean DAO state with one valid report target.
		UserDAO.getInstance().clear();
		PostDAO.getInstance().clear();
		ReportDAO.getInstance().clear();

		reporter = new User(UUID.randomUUID(), User.Role.Member, "reporter", "password");
		otherReporter = new User(UUID.randomUUID(), User.Role.Member, "otherReporter", "password");
		post = new Post(UUID.randomUUID(), reporter.id(), "Task 5 test post");
		message = new Message(UUID.randomUUID(), reporter.id(), post.id, 100L, "message under test");

		assertTrue(UserDAO.getInstance().add(reporter));
		assertTrue(UserDAO.getInstance().add(otherReporter));
		assertTrue(PostDAO.getInstance().add(post));
		assertTrue(post.messages.insert(message));
	}

	@After
	public void tearDown() {
		UserDAO.getInstance().clear();
		PostDAO.getInstance().clear();
		ReportDAO.getInstance().clear();
	}

	@Test(timeout = 1000)
	public void addReportSucceedsForExistingMessageAndUser() {
		// Valid message, valid user, and no previous report is the success path.
		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(1, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportRejectsMissingMessage() {
		assertFalse(ModerationTools.addReport(UUID.randomUUID(), reporter.id(), 10L));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(0, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportRejectsMissingMessageWhenNoPostsExist() {
		// Covers message lookup when there are no posts to search.
		PostDAO.getInstance().clear();

		assertFalse(ModerationTools.addReport(UUID.randomUUID(), reporter.id(), 10L));

		assertEquals(0, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportRejectsNullMessage() {
		assertFalse(ModerationTools.addReport(null, reporter.id(), 10L));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(0, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportRejectsMissingUser() {
		assertFalse(ModerationTools.addReport(message.id(), UUID.randomUUID(), 10L));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(0, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportRejectsNullUser() {
		assertFalse(ModerationTools.addReport(message.id(), null, 10L));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(0, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportRejectsWhenBothIdsAreNull() {
		assertFalse(ModerationTools.addReport(null, null, 10L));

		assertEquals(0, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportFindsMessageAfterSkippingEarlierNonMatchingMessage() {
		// The lookup must continue past non-matching messages.
		Message earlierMessage = new Message(UUID.randomUUID(), reporter.id(), post.id, 50L, "not reported");
		assertTrue(post.messages.insert(earlierMessage));

		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));

		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
		assertFalse(ModerationTools.hasReported(earlierMessage.id(), reporter.id()));
	}

	@Test(timeout = 1000)
	public void addReportRejectsDuplicateReportFromSameUser() {
		// A user may only have one active report on a message.
		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertFalse(ModerationTools.addReport(message.id(), reporter.id(), 20L));
		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(1, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportSucceedsAfterReportWasRetracted() {
		// After removal, the same user may report the same message again.
		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.removeReport(message.id(), reporter.id(), 0L));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));

		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 30L));
		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
		assertEquals(1, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportAllowsDifferentUsersToReportSameMessage() {
		// Multiple users may independently report the same message.
		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.addReport(message.id(), otherReporter.id(), 20L));
		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
		assertTrue(ModerationTools.hasReported(message.id(), otherReporter.id()));
		assertEquals(2, activeReportCount());
	}

	@Test(timeout = 1000)
	public void addReportAllowsSameUserToReportDifferentMessages() {
		// Duplicate detection is per message, not per user globally.
		Message secondMessage = new Message(
				UUID.randomUUID(),
				reporter.id(),
				post.id,
				200L,
				"second message"
		);
		assertTrue(post.messages.insert(secondMessage));

		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.addReport(secondMessage.id(), reporter.id(), 20L));

		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
		assertTrue(ModerationTools.hasReported(secondMessage.id(), reporter.id()));
		assertEquals(2, activeReportCount());
	}

	@Test(timeout = 1000)
	public void hasReportedCoversAllGuardBranches() {
		// Mirrors addReport's validation guards for report state checks.
		assertFalse(ModerationTools.hasReported(null, reporter.id()));
		assertFalse(ModerationTools.hasReported(message.id(), null));
		assertFalse(ModerationTools.hasReported(null, null));
		assertFalse(ModerationTools.hasReported(UUID.randomUUID(), reporter.id()));
		assertFalse(ModerationTools.hasReported(message.id(), UUID.randomUUID()));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));

		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
	}

	@Test(timeout = 1000)
	public void removeReportCoversAllGuardBranches() {
		// Removal should reject invalid UUIDs and missing active reports.
		assertFalse(ModerationTools.removeReport(null, reporter.id(), 0L));
		assertFalse(ModerationTools.removeReport(message.id(), null, 0L));
		assertFalse(ModerationTools.removeReport(null, null, 0L));
		assertFalse(ModerationTools.removeReport(UUID.randomUUID(), reporter.id(), 0L));
		assertFalse(ModerationTools.removeReport(message.id(), UUID.randomUUID(), 0L));
		assertFalse(ModerationTools.removeReport(message.id(), reporter.id(), 0L));

		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.removeReport(message.id(), reporter.id(), 0L));
		assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));
	}

	@Test(timeout = 1000)
	public void setHiddenCoversAllGuardBranchesAndUpdatesState() {
		// Hidden state changes are permitted only for existing admin users.
		User admin = new User(UUID.randomUUID(), User.Role.Admin, "admin", "password");
		assertTrue(UserDAO.getInstance().add(admin));

		assertFalse(ModerationTools.setHidden(null, admin.id(), true));
		assertFalse(ModerationTools.setHidden(message.id(), null, true));
		assertFalse(ModerationTools.setHidden(null, null, true));
		assertFalse(ModerationTools.setHidden(message.id(), UUID.randomUUID(), true));
		assertFalse(ModerationTools.setHidden(message.id(), reporter.id(), true));
		assertFalse(ModerationTools.setHidden(UUID.randomUUID(), admin.id(), true));

		assertFalse(message.isHidden());
		assertTrue(ModerationTools.setHidden(message.id(), admin.id(), true));
		assertTrue(message.isHidden());
		assertTrue(ModerationTools.setHidden(message.id(), admin.id(), false));
		assertFalse(message.isHidden());
	}

	@Test(timeout = 1000)
	public void reportedMessagesEntryPointAcceptsValidArguments() {
		// Do not assert the placeholder result; Task 4 defines this behaviour.
		ModerationTools.getReportedMessages("OLDEST", 1);
	}

	@Test(timeout = 1000)
	public void reportedMessagesReturnsExistingReportedMessage() {
		// Covers the branch where a ranked report bucket resolves to a real message.
		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));

		Iterator<Message> reported = ModerationTools.getReportedMessages("MOST", 1);

		assertTrue(reported.hasNext());
		assertEquals(message.id(), reported.next().id());
		assertFalse(reported.hasNext());
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void reportedMessagesRejectsZeroAmount() {
		// Amount must be positive before any strategy or report lookup is attempted.
		ModerationTools.getReportedMessages("OLDEST", 0);
	}

	@Test(expected = IllegalArgumentException.class, timeout = 1000)
	public void reportedMessagesRejectsUnknownStrategy() {
		// Covers the factory's rejected strategy branch.
		ModerationTools.getReportedMessages("NEWEST", 1);
	}

	@Test(timeout = 1000)
	public void reportedMessagesSkipsEmptyReportBuckets() {
		// Removing the last report leaves an empty bucket that should be ignored.
		assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
		assertTrue(ModerationTools.removeReport(message.id(), reporter.id(), 0L));

		assertFalse(ModerationTools.getReportedMessages("OLDEST", 1).hasNext());
	}

	@Test(timeout = 1000)
	public void reportedMessagesSkipsReportsForMissingMessages() {
		// Persistence may restore a report before its message exists; it must not be returned.
		ReportDAO.getInstance().addExisting(new Report(UUID.randomUUID(), reporter.id(), 10L));

		assertFalse(ModerationTools.getReportedMessages("OLDEST", 1).hasNext());
	}

	@Test(timeout = 1000)
	public void privateMessageLookupHandlesNullInput() throws Exception {
		// Reflection reaches the helper branch not reachable through addReport's null guard.
		Method getMessageByUUID = ModerationTools.class.getDeclaredMethod("getMessageByUUID", UUID.class);
		getMessageByUUID.setAccessible(true);

		assertNull(getMessageByUUID.invoke(null, new Object[]{null}));
	}

	@Test(timeout = 1000)
	public void messageComparatorCoversEveryComparisonBranch() {
		// Message ordering falls through timestamp, thread, poster, then id.
		Message base = new Message(uuid(10), uuid(20), uuid(30), 100L, "base");

		assertTrue(MessageComparator.getInstance().compare(
				base,
				new Message(uuid(10), uuid(20), uuid(30), 200L, "timestamp")) < 0);
		assertTrue(MessageComparator.getInstance().compare(
				base,
				new Message(uuid(10), uuid(20), uuid(31), 100L, "thread")) < 0);
		assertTrue(MessageComparator.getInstance().compare(
				base,
				new Message(uuid(10), uuid(21), uuid(30), 100L, "poster")) < 0);
		assertTrue(MessageComparator.getInstance().compare(
				base,
				new Message(uuid(11), uuid(20), uuid(30), 100L, "id")) < 0);
		assertEquals(0, MessageComparator.getInstance().compare(
				base,
				new Message(uuid(10), uuid(20), uuid(30), 100L, "same comparator fields")));
	}

	@Test(timeout = 1000)
	public void messageSerializerCoversHiddenAndVisibleMessages() {
		// Hidden messages persist with a portable "1"/"0" flag.
		MessageSerializer serializer = new MessageSerializer();
		Message visible = new Message(uuid(1), uuid(2), uuid(3), 123L, "visible", false);
		Message hidden = new Message(uuid(4), uuid(5), uuid(6), 456L, "hidden", true);

		assertArrayEquals(new String[]{
				uuid(1).toString(), uuid(2).toString(), uuid(3).toString(), "123", "visible", "0"
		}, serializer.serialize(visible));
		assertArrayEquals(new String[]{
				uuid(4).toString(), uuid(5).toString(), uuid(6).toString(), "456", "hidden", "1"
		}, serializer.serialize(hidden));

		Message restoredVisible = serializer.deserialize(serializer.serialize(visible));
		Message restoredHidden = serializer.deserialize(serializer.serialize(hidden));
		assertEquals(visible.id(), restoredVisible.id());
		assertEquals(visible.poster(), restoredVisible.poster());
		assertEquals(visible.thread(), restoredVisible.thread());
		assertEquals(visible.timestamp(), restoredVisible.timestamp());
		assertEquals(visible.message(), restoredVisible.message());
		assertFalse(restoredVisible.isHidden());
		assertTrue(restoredHidden.isHidden());
	}

	@Test(timeout = 1000)
	public void reportSerializerRoundTripsReportFields() {
		// Reports persist as message id, user id, and timestamp.
		ReportSerializer serializer = new ReportSerializer();
		Report report = new Report(uuid(7), uuid(8), 999L);

		assertArrayEquals(new String[]{uuid(7).toString(), uuid(8).toString(), "999"}, serializer.serialize(report));

		Report restored = serializer.deserialize(serializer.serialize(report));
		assertEquals(report.message(), restored.message());
		assertEquals(report.user(), restored.user());
		assertEquals(report.timestamp(), restored.timestamp());
	}

	@Test(timeout = 1000)
	public void dataManagerCoversSingletonReadAndWritePaths() throws Exception {
		// Fake pipelines avoid filesystem side effects while exercising persistence flow.
		Field instance = DataManager.class.getDeclaredField("instance");
		instance.setAccessible(true);
		instance.set(null, null);

		DataManager first = DataManager.getInstance();
		DataManager second = DataManager.getInstance();
		assertNotNull(first);
		assertSame(first, second);

		DataManager manager = newDataManagerViaReflection();
		UUID savedPostId = UUID.randomUUID();
		User savedUser = new User(UUID.randomUUID(), User.Role.Member, "savedUser", "password");
		Post savedPost = new Post(savedPostId, savedUser.id(), "saved post");
		Message savedMessage = new Message(UUID.randomUUID(), savedUser.id(), savedPostId, 321L, "saved message");
		Report savedReport = new Report(savedMessage.id(), savedUser.id(), 654L);

		replacePipeline(manager, "userPipeline", new CoveragePipeline<>(savedUser));
		replacePipeline(manager, "postPipeline", new CoveragePipeline<>(savedPost));
		replacePipeline(manager, "messagePipeline", new CoveragePipeline<>(savedMessage));
		replacePipeline(manager, "reportPipeline", new CoveragePipeline<>(savedReport));

		manager.readAll();
		assertEquals(savedUser.id(), UserDAO.getInstance().getByUUID(savedUser.id()).id());
		assertTrue(ModerationTools.hasReported(savedMessage.id(), savedUser.id()));

		manager.writeAll();
	}

	private static int activeReportCount() {
		// Counts active reports without relying on their iteration order.
		int count = 0;
		Iterator<Report> reports = ReportDAO.getInstance().allReports();
		while (reports.hasNext()) {
			reports.next();
			count++;
		}
		return count;
	}

	private static DataManager newDataManagerViaReflection() throws Exception {
		// DataManager has a private constructor; reflection allows the test to
		// build an isolated instance without changing production code visibility.
		Constructor<DataManager> constructor = DataManager.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		return constructor.newInstance();
	}

	private static void replacePipeline(DataManager manager, String fieldName, DataPipeline<?, String[]> pipeline)
			throws Exception {
		Field field = DataManager.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(manager, pipeline);
	}

	private static UUID uuid(int lastByte) {
		return new UUID(0L, lastByte);
	}

	private static class CoveragePipeline<T> extends DataPipeline<T, String[]> {
		// Minimal test double for DataManager's read and write callbacks.
		private final T item;

		CoveragePipeline(T item) {
			super(null, null, null, "coverage");
			this.item = item;
		}

		@Override
		public void readTo(AddToDAO<T> callback) {
			callback.run(item);
		}

		@Override
		public void writeFrom(Iterator<T> iterator) {
			while (iterator.hasNext()) {
				iterator.next();
			}
		}
	}
}