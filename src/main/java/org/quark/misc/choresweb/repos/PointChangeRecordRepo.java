package org.quark.misc.choresweb.repos;

import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.entities.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface PointChangeRecordRepo extends JpaRepository<PointChangeRecord, Long> {
	default Slice<PointChangeRecord> getWorkerHistory(@Param("member") Membership worker, Pageable pageable) {
		return getByOrganizationAndWorker(worker.getId().getOrganization(), worker.getId().getMember(), pageable);
	}

	Slice<PointChangeRecord> getByOrganizationAndWorker(Organization org, User worker, Pageable pageable);

	default Slice<PointChangeRecord> getJobHistory(Job job, Pageable pageable) {
		return getByOrganizationAndChangeTypeAndChangeSourceId(job.getOrganization(), PointChangeRecord.PointChangeType.Job, job.getId(),
			pageable);
	}

	default Slice<PointChangeRecord> getResourceHistory(PointResource rsrc, Pageable pageable) {
		return getByOrganizationAndChangeTypeAndChangeSourceId(rsrc.getOrganization(), PointChangeRecord.PointChangeType.Resource,
			rsrc.getId(), pageable);
	}

	Slice<PointChangeRecord> getByOrganizationAndChangeTypeAndChangeSourceId(Organization org, PointChangeRecord.PointChangeType changeType,
		long changeSourceId, Pageable pageable);

	default void deleteForMember(Membership member) {
		deleteByOrganizationAndWorker(member.getId().getOrganization(), member.getId().getMember());
	}

	void deleteByOrganizationAndWorker(Organization org, User worker);

	void deleteByOrganization(Organization organization);
}
