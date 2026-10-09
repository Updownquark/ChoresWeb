package org.quark.misc.choresweb.repos;

import java.util.List;
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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

public interface PointChangeRecordRepo extends JpaRepository<PointChangeRecord, Long> {
	static final String SELECT_DTO = "SELECT new org.quark.misc.choresweb.entities.PointChangeRecord$FullPcrDto("//
		+ "pcr.id, pcr.worker.id, pcr.organization.id, pcr.changeType, pcr.changeSourceId, pcr.time, pcr.changeSourceName,"
		+ " pcr.beforePoints, pcr.pointChange, pcr.quantity, pcr.valueOrRate, pcr.notes) FROM PointChangeRecord pcr";

	@Query("SELECT new org.quark.misc.choresweb.entities.PointChangeRecord$PcrKeyDto("//
		+ "pcr.worker.id, pcr.changeType, pcr.changeSourceId, pcr.time)"//
		+ " FROM PointChangeRecord pcr WHERE organization.id=:orgId")
	Stream<PointChangeRecord.PcrKeyDto> keysByOrganization(@Param("orgId") long orgId);

	@Query("FROM PointChangeRecord pcr"//
		+ " WHERE pcr.organization.id=:orgId"//
		+ " AND pcr.worker.id=:workerId"//
		+ " AND pcr.time>=:time"//
		+ " ORDER BY pcr.id")
	List<PointChangeRecord> getWorkerHistoryAfter(@Param("orgId") long orgId, @Param("workerId") long workerId, @Param("time") long time);

	@Query(SELECT_DTO //
		+ " WHERE pcr.organization.id=:orgId")
	Slice<PointChangeRecord.FullPcrDto> getOrganizationHistory(@Param("orgId") long orgId, Pageable pageable);

	@Query(SELECT_DTO//
		+ " WHERE pcr.organization.id=:orgId"//
		+ " AND pcr.worker.id=:workerId")
	Slice<PointChangeRecord.FullPcrDto> getWorkerHistory(@Param("orgId") long orgId, @Param("workerId") long workerId, Pageable pageable);

	default Slice<PointChangeRecord.FullPcrDto> getJobHistory(Job job, Pageable pageable) {
		return getChangeSourceHistory(job.getOrganization().getId(), PointChangeRecord.PointChangeType.Job, job.getId(), pageable);
	}

	default Slice<PointChangeRecord.FullPcrDto> getResourceHistory(PointResource rsrc, Pageable pageable) {
		return getChangeSourceHistory(rsrc.getOrganization().getId(), PointChangeRecord.PointChangeType.Resource, rsrc.getId(), pageable);
	}

	@Query(SELECT_DTO //
		+ " WHERE pcr.organization.id=:orgId"//
		+ " AND pcr.changeType=:changeType"//
		+ " AND pcr.changeSourceId=:changeSourceId")
	Slice<PointChangeRecord.FullPcrDto> getChangeSourceHistory(@Param("orgId") long orgId,
		@Param("changeType") PointChangeRecord.PointChangeType changeType, @Param("changeSourceId") long changeSourceId, Pageable pageable);

	@Query(SELECT_DTO//
		+ " WHERE pcr.organization.id=:orgId"//
		+ " AND pcr.worker.id=:workerId"//
		+ " AND pcr.changeType=:changeType" //
		+ " AND pcr.changeSourceId=:changeSourceId")
	Slice<PointChangeRecord.FullPcrDto> getChangeSourceHistoryForWorker(@Param("orgId") long orgId, @Param("workerId") long workerId,
		@Param("changeType") PointChangeRecord.PointChangeType changeType, @Param("changeSourceId") long changeSourceId, Pageable pageable);

	@Query("SELECT COUNT(*) FROM PointChangeRecord pcr" //
		+ " WHERE pcr.organization.id=:orgId")
	int getOrganizationHistorySize(@Param("orgId") long orgId);

	@Query("SELECT COUNT(*) FROM PointChangeRecord pcr" //
		+ " WHERE pcr.organization.id=:orgId AND pcr.worker.id=:workerId")
	int getWorkerHistorySize(@Param("orgId") long orgId, @Param("workerId") long workerId);

	@Query("SELECT COUNT(*) FROM PointChangeRecord pcr" //
		+ " WHERE pcr.organization.id=:orgId"//
		+ " AND pcr.changeType=:changeType"//
		+ " AND pcr.changeSourceId=:changeSourceId")
	int getChangeSourceHistorySize(@Param("orgId") long orgId, @Param("changeType") PointChangeRecord.PointChangeType changeType,
		@Param("changeSourceId") long changeSourceId);

	@Query("SELECT COUNT(*) FROM PointChangeRecord pcr" //
		+ " WHERE pcr.organization.id=:orgId AND pcr.worker.id=:workerId"//
		+ " AND pcr.changeType=:changeType" //
		+ " AND pcr.changeSourceId=:changeSourceId")
	int getChangeSourceHistorySizeForWorker(@Param("orgId") long orgId, @Param("workerId") long workerId,
		@Param("changeType") PointChangeRecord.PointChangeType changeType, @Param("changeSourceId") long changeSourceId);

	@Query("FROM PointChangeRecord WHERE organization=:org")
	@QueryHints(@QueryHint(name = "jakarta.persistence.fetchSize", value = "500"))
	Stream<PointChangeRecord> getOrgHistory(@Param("org") Organization org);

	@Modifying
	default void deleteForMember(Membership member) {
		deleteByOrganizationAndWorker(member.getOrganization(), member.getMember());
	}

	@Modifying
	void deleteByOrganizationAndWorker(Organization org, User worker);

	@Modifying
	void deleteByOrganization(Organization organization);
}
