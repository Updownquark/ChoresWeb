package org.quark.misc.choresweb.repos;

import java.util.stream.Stream;

import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.entities.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointChangeRecordRepo extends JpaRepository<PointChangeRecord, Long> {
	static final String SELECT_DTO = "SELECT new org.quark.misc.choresweb.entities.PointChangeRecord$FullPcrDto("//
		+ "pcr.id, pcr.worker.id, pcr.changeType, pcr.changeSourceId, pcr.time, pcr.changeSourceName, pcr.beforePoints, pcr.pointChange,"//
		+ " pcr.quantity, pcr.valueOrRate) FROM PointChangeRecord pcr";

	@Query("SELECT new org.quark.misc.choresweb.entities.PointChangeRecord$PcrKeyDto("//
		+ "pcr.worker.id, pcr.changeType, pcr.changeSourceId, pcr.time)"//
		+ " FROM PointChangeRecord pcr WHERE organization.id=:orgId")
	Stream<PointChangeRecord.PcrKeyDto> keysByOrganization(@Param("orgId") long orgId);

	default Slice<PointChangeRecord.FullPcrDto> getWorkerHistory(@Param("member") Membership worker, Pageable pageable) {
		return getByOrganizationAndWorker(worker.getId().getOrganization(), worker.getId().getMember(), pageable);
	}

	@Query(SELECT_DTO + " WHERE pcr.organization=:org AND pcr.worker=:worker")
	Slice<PointChangeRecord.FullPcrDto> getByOrganizationAndWorker(@Param("org") Organization org, @Param("worker") User worker,
		Pageable pageable);

	default Slice<PointChangeRecord.FullPcrDto> getJobHistory(Job job, Pageable pageable) {
		return getByOrganizationAndChangeTypeAndChangeSourceId(job.getOrganization(), PointChangeRecord.PointChangeType.Job, job.getId(),
			pageable);
	}

	default Slice<PointChangeRecord.FullPcrDto> getResourceHistory(PointResource rsrc, Pageable pageable) {
		return getByOrganizationAndChangeTypeAndChangeSourceId(rsrc.getOrganization(), PointChangeRecord.PointChangeType.Resource,
			rsrc.getId(), pageable);
	}

	@Query(SELECT_DTO + " WHERE pcr.organization=:org AND pcr.changeType=:changeType AND pcr.changeSourceId=:changeSourceId")
	Slice<PointChangeRecord.FullPcrDto> getByOrganizationAndChangeTypeAndChangeSourceId(@Param("org") Organization org,
		@Param("changeType") PointChangeRecord.PointChangeType changeType, @Param("changeSourceId") long changeSourceId, Pageable pageable);

	default void deleteForMember(Membership member) {
		deleteByOrganizationAndWorker(member.getId().getOrganization(), member.getId().getMember());
	}

	void deleteByOrganizationAndWorker(Organization org, User worker);

	void deleteByOrganization(Organization organization);
}
