package org.quark.misc.choresweb.svc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointChangeRecord.PointChangeType;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Service
public class PointHistoryService {
	private final MembershipRepo theMembershipRepo;
	private final UserService theUserSvc;
	private final PointChangeRecordRepo theHistoryRepo;
	private final NavigableSet<HistoryChange> theChanges;
	private final EntityManager entityManager;
	private long theChangePersistenceTime = 15000;

	public PointHistoryService(MembershipRepo membershipRepo, UserService userSvc, PointChangeRecordRepo historyRepo,
		NavigableSet<HistoryChange> changes, EntityManager entityManager) {
		theMembershipRepo = membershipRepo;
		theUserSvc = userSvc;
		theHistoryRepo = historyRepo;
		theChanges = changes;
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public List<PointChangeRecord.FullPcrDto> getHistory(Membership me, Long userId, Long jobId, Long resourceId, int pageSize,
		int pageNumber) {
		if (jobId != null && resourceId != null)
			throw new IllegalArgumentException("jobId and resourceId may not both be specified");
		long orgId = me.getId().getOrganization().getId();
		Pageable page = PageRequest.of(pageNumber, pageSize, Sort.by("time", "id").descending());
		Slice<PointChangeRecord.FullPcrDto> data;
		if (userId != null) {
			if (jobId != null)
				data = theHistoryRepo.getChangeSourceHistoryForWorker(orgId, userId, PointChangeType.Job, jobId, page);
			else if (resourceId != null)
				data = theHistoryRepo.getChangeSourceHistoryForWorker(orgId, userId, PointChangeType.Resource, resourceId, page);
			else
				data = theHistoryRepo.getWorkerHistory(orgId, userId, page);
		} else {
			if (jobId != null)
				data = theHistoryRepo.getChangeSourceHistory(orgId, PointChangeType.Job, jobId, page);
			else if (resourceId != null)
				data = theHistoryRepo.getChangeSourceHistory(orgId, PointChangeType.Resource, resourceId, page);
			else
				data = theHistoryRepo.getOrganizationHistory(orgId, page);
		}
		return data.getContent();
	}

	@Transactional(readOnly = true)
	public int getHistorySize(Membership me, Long userId, Long jobId, Long resourceId) {
		if (jobId != null && resourceId != null)
			throw new IllegalArgumentException("jobId and resourceId may not both be specified");
		long orgId = me.getId().getOrganization().getId();
		if (userId != null) {
			if (jobId != null)
				return theHistoryRepo.getChangeSourceHistorySizeForWorker(orgId, userId, PointChangeType.Job, jobId);
			else if (resourceId != null)
				return theHistoryRepo.getChangeSourceHistorySizeForWorker(orgId, userId, PointChangeType.Resource, resourceId);
			else
				return theHistoryRepo.getWorkerHistorySize(orgId, userId);
		} else {
			if (jobId != null)
				return theHistoryRepo.getChangeSourceHistorySize(orgId, PointChangeType.Job, jobId);
			else if (resourceId != null)
				return theHistoryRepo.getChangeSourceHistorySize(orgId, PointChangeType.Resource, resourceId);
			else
				return theHistoryRepo.getOrganizationHistorySize(orgId);
		}
	}

	@Transactional
	public void historyAdded(List<PointChangeRecord> changes) {
		theHistoryRepo.saveAll(changes);
		publishChanges(changes);
	}

	private synchronized void publishChanges(Iterable<PointChangeRecord> changes) {
		long time = System.nanoTime();
		theChanges.headSet(new HistoryChange(time - theChangePersistenceTime, 0, 0, 0, 0), false).clear();
		if (!theChanges.isEmpty()) {
			var last = theChanges.last();
			if (last.changeTime >= time)
				time = last.changeTime + 1;
		}
		for (PointChangeRecord change : changes) {
			switch (change.getChangeType()) {
			case Job:
				theChanges.add(
					new HistoryChange(time, change.getOrganization().getId(), change.getWorker().getId(), change.getChangeSourceId(), -1));
				break;
			case Resource:
				theChanges.add(
					new HistoryChange(time, change.getOrganization().getId(), change.getWorker().getId(), -1, change.getChangeSourceId()));
				break;
			default:
				theChanges.add(new HistoryChange(time, change.getOrganization().getId(), change.getWorker().getId(), -1, -1));
				break;
			}
			entityManager.detach(change);
			time++;
		}
	}

	public synchronized HistoryChanges getChanges(long orgId, long lastKnownChange) {
		SortedSet<Long> userIds = null, jobIds = null, resourceIds = null;
		long lastTime = lastKnownChange;
		for (HistoryChange change : theChanges.tailSet(new HistoryChange(lastKnownChange, 0, 0, 0, 0), false)) {
			if (change.orgId != orgId)
				continue;
			lastTime = change.changeTime;
			if (change.userId != -1) {
				if (userIds == null)
					userIds = new TreeSet<>();
				userIds.add(change.userId);
			}
			if (change.jobId != -1) {
				if (jobIds == null)
					jobIds = new TreeSet<>();
				jobIds.add(change.jobId);
			}
			if (change.resourceId != -1) {
				if (resourceIds == null)
					resourceIds = new TreeSet<>();
				resourceIds.add(change.resourceId);
			}
		}
		return new HistoryChanges(lastTime, //
			userIds == null ? Collections.emptySet() : userIds, //
			jobIds == null ? Collections.emptySet() : jobIds, //
			resourceIds == null ? Collections.emptySet() : resourceIds);
	}

	@Transactional
	public synchronized void revertHistory(Membership me, List<Long> items) {
		// Retrieve history items, sorted by time/ID
		List<PointChangeRecord> history = new ArrayList<>();
		for (Long item : items) {
			PointChangeRecord pcr = theHistoryRepo.findById(item).orElse(null);
			if (pcr == null)
				throw new IllegalArgumentException("No such history with ID " + item);
			int index = Collections.binarySearch(history, pcr);
			if (index < 0)
				index = -index - 1;
			history.add(index, pcr);
		}
		// Retrieve relevant members and update their points
		class MemberChanges {
			final Membership member;
			final List<PointChangeRecord> changes;

			MemberChanges(Membership member) {
				this.member = member;
				changes = new ArrayList<>();
			}
		}
		Map<Long, MemberChanges> members = new HashMap<>();
		for (PointChangeRecord change : history) {
			if (change.getWorker() == null)
				return;
			MemberChanges member = members.get(change.getWorker().getId());
			if (member == null) {
				Membership m = theUserSvc.getMembership(me.getId().getOrganization().getId(), change.getWorker().getId());
				if (m == null)
					continue;
				member = new MemberChanges(m);
				members.put(change.getWorker().getId(), member);
			}
			member.changes.add(change);
			member.member.setPoints(member.member.getPoints() - change.getPointChange());
		}

		theHistoryRepo.deleteAll(history); // Delete the history entities

		theMembershipRepo.saveAll(members.values().stream().map(m -> m.member).toList());// Update member points in the database
		for (MemberChanges member : members.values()) {
			theUserSvc.memberUpdated(member.member); // Update user service consumers
			// Rewrite point history to be consistent
			List<PointChangeRecord> affected = theHistoryRepo.getWorkerHistoryAfter(//
				me.getId().getOrganization().getId(), //
				member.member.getId().getMember().getId(), member.changes.get(0).getTime().toEpochMilli());
			Iterator<PointChangeRecord> deleted = member.changes.iterator();
			PointChangeRecord currentDeleted = deleted.next();
			int pointDelta = -currentDeleted.getPointChange();
			boolean first = true;
			for (PointChangeRecord change : affected) {
				if (first && change.compareTo(currentDeleted) < 0)
					continue;
				if (currentDeleted != null && change.compareTo(currentDeleted) > 0) {
					if (deleted.hasNext()) {
						currentDeleted = deleted.next();
						pointDelta -= currentDeleted.getPointChange();
					} else
						currentDeleted = null;
				}
				change.setBeforePoints(change.getBeforePoints() + pointDelta);
			}
			theHistoryRepo.saveAll(affected);
		}

		publishChanges(history); // Alert history service consumers
	}

	static class HistoryChange implements Comparable<HistoryChange> {
		final long changeTime;
		final long orgId;
		final long userId;
		final long jobId;
		final long resourceId;

		HistoryChange(long changeTime, long orgId, long userId, long jobId, long resourceId) {
			this.changeTime = changeTime;
			this.orgId = orgId;
			this.userId = userId;
			this.jobId = jobId;
			this.resourceId = resourceId;
		}

		@Override
		public int compareTo(HistoryChange o) {
			return Long.compare(changeTime, o.changeTime);
		}
	}

	public static class HistoryChanges {
		public final long lastChangeTime;
		public final Set<Long> userIds;
		public final Set<Long> jobIds;
		public final Set<Long> resourceIds;

		HistoryChanges(long lastChangeTime, Set<Long> userIds, Set<Long> jobIds, Set<Long> resourceIds) {
			this.lastChangeTime = lastChangeTime;
			this.userIds = userIds;
			this.jobIds = jobIds;
			this.resourceIds = resourceIds;
		}
	}
}
