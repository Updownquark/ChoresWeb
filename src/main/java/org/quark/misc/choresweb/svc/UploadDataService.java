package org.quark.misc.choresweb.svc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.qommons.TimeUtils;
import org.qommons.ex.ExSupplier;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.CsvParser;
import org.qommons.io.FileUtils;
import org.qommons.io.InMemoryFileSystem;
import org.qommons.io.TextParseException;
import org.quark.misc.choresweb.entities.Assignment;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointChangeRecord.PointChangeType;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadDataService {
	private final JobRepo theJobRepo;
	private final MembershipRepo theMembershipRepo;
	private final JobService theJobService;
	private final UserService theUserSvc;
	private final PointResourceService theResourceSvc;
	private final WorkService theWorkSvc;
	private final PointHistoryService theHistorySvc;
	private final PointChangeRecordRepo theHistoryRepo;
	private final AssignmentRepo theAssnRepo;
	private final PointResourceRepo theResourceRepo;

	public UploadDataService(JobRepo jobRepo, MembershipRepo membershipRepo, JobService jobService, UserService userSvc,
		PointResourceService resourceSvc, WorkService workSvc, PointHistoryService historySvc, PointChangeRecordRepo historyRepo,
		AssignmentRepo assnRepo, PointResourceRepo resourceRepo) {
		theJobRepo = jobRepo;
		theMembershipRepo = membershipRepo;
		theJobService = jobService;
		theUserSvc = userSvc;
		theResourceSvc = resourceSvc;
		theWorkSvc = workSvc;
		theHistorySvc = historySvc;
		theHistoryRepo = historyRepo;
		theAssnRepo = assnRepo;
		theResourceRepo = resourceRepo;
	}

	@Transactional
	public void uploadBackup(Organization org, ExSupplier<InputStream, IOException> file) {
		InMemoryFileSystem baseFS = new InMemoryFileSystem();
		BetterFile zipFile = baseFS.at("/file.zip");
		try {
			FileUtils.copy(file, zipFile::write);
			BetterFile archive = new ArchiveEnabledFileSource(baseFS)//
				.withArchival(new ArchiveEnabledFileSource.ZipCompression())//
				.at(zipFile.getPath());

			System.out.println("Importing backup data for " + org.getName() + "...");

			System.out.println("\tImporting jobs...");
			Map<String, Job> jobsByName = new HashMap<>();
			for (Job job : theJobRepo.getOrgJobs(org))
				jobsByName.put(job.getName(), job);
			Map<Long, Job> jobs = new HashMap<>();
			List<Job> newJobs = new ArrayList<>();
			int preExisting = 0;
			try (CsvParser jobsFile = new CsvParser(new InputStreamReader(archive.at("Job.csv").read(), StandardCharsets.UTF_8), ',', 0)) {
				var typedParser = jobsFile.parseTyped()//
					.with("id", false, s -> Long.valueOf(s))//
					.with("name", false, s -> s)//
					.with("active", false, s -> "true".equalsIgnoreCase(s))//
					.with("exclusionLabels", false, s -> s)//
					.with("inclusionLabels", false, s -> s)//
					.with("minLevel", false, s -> s.equals("null") ? 0 : Integer.valueOf(s))//
					.with("maxLevel", false, s -> s.equals("null") ? 100 : Integer.valueOf(s))//
					.with("points", false, s -> Integer.valueOf(s))//
					.with("priority", false, s -> Integer.valueOf(s))//
					.with("lastDone", false, s -> TimeUtils.parseInstant(s, true, true, teo -> teo.localTime()).evaluate(Instant::now))//
				;
				for (var line = typedParser.parseNextLine(); line != null; line = typedParser.parseNextLine()) {
					String name = line.getValue2();
					Job job = jobsByName.get(name);
					if (job == null) {
						job = new Job(org, name);
						job.setActive(line.getValue3());
						job.setExclusionLabels(line.get(3, String.class));
						job.setInclusionLabels(line.get(4, String.class));
						job.setMinLevel(line.get(5, Integer.class));
						job.setMaxLevel(line.get(6, Integer.class));
						job.setValue(line.get(7, int.class));
						job.setPriority(line.get(8, int.class));
						job.setLastDone(line.get(9, Instant.class));
						newJobs.add(job);
					} else
						preExisting++;
					jobs.put(line.getValue1(), job);
				}
			}
			if (!newJobs.isEmpty()) {
				theJobRepo.saveAll(newJobs);
				for (Job job : newJobs)
					theJobService.jobUpdated(job);
			}
			System.out.println("\t\t" + newJobs.size() + " added, " + preExisting + " pre-existing");

			System.out.println("\tImporting workers...");
			preExisting = 0;
			Map<Long, Membership> workers = new HashMap<>();
			List<Membership> newWorkers = new ArrayList<>();
			try (CsvParser workersFile = new CsvParser(new InputStreamReader(archive.at("Worker.csv").read(), StandardCharsets.UTF_8), ',',
				0)) {
				var typedParser = workersFile.parseTyped()//
					.with("id", false, s -> Long.valueOf(s))//
					.with("name", false, s -> s)//
					.with("excessPoints", false, s -> Long.valueOf(s))//
					.with("level", false, s -> Integer.valueOf(s))//
					.with("labels", false, s -> s)//
				;
				for (var line = typedParser.parseNextLine(); line != null; line = typedParser.parseNextLine()) {
					User user = theUserSvc.getOrCreateUser(line.getValue2());
					Membership membership = theMembershipRepo.getMembership(user.getId(), org.getId());
					if (membership == null) {
						membership = new Membership(org, user);
						membership.setName(line.getValue2());
						membership.setWorker(true);
						membership.setPoints(line.get(2, long.class));
						membership.setLevel(line.get(3, int.class));
						membership.setLabels(line.get(4, String.class));
						newWorkers.add(membership);
					} else
						preExisting++;
					workers.put(line.getValue1(), membership);
				}
			}
			if (!newWorkers.isEmpty()) {
				theMembershipRepo.saveAll(newWorkers);
				for (Membership worker : newWorkers)
					theUserSvc.memberUpdated(worker);
			}
			System.out.println("\t\t" + newWorkers.size() + " added, " + preExisting + " pre-existing");

			System.out.println("\tImporting Point Resources...");
			preExisting = 0;
			Map<String, PointResource> resourceByName = new HashMap<>();
			for (PointResource rsrc : theResourceRepo.getOrgResources(org))
				resourceByName.put(rsrc.getName(), rsrc);
			Map<Long, PointResource> resources = new HashMap<>();
			List<PointResource> newRsrcs = new ArrayList<>();
			try (CsvParser resourcesFile = new CsvParser(
				new InputStreamReader(archive.at("PointResource.csv").read(), StandardCharsets.UTF_8), ',', 0)) {
				var typedParser = resourcesFile.parseTyped()//
					.with("id", false, s -> Long.valueOf(s))//
					.with("name", false, s -> s)//
					.with("rate", false, s -> Double.valueOf(s))//
					.with("unit", false, s -> s.equals("null") ? null : s)//
				;
				for (var line = typedParser.parseNextLine(); line != null; line = typedParser.parseNextLine()) {
					String name = line.getValue2();
					PointResource rsrc = resourceByName.get(name);
					if (rsrc == null) {
						rsrc = new PointResource(org, name);
						rsrc.setRate(line.get(2, double.class));
						rsrc.setUnit(line.get(3, String.class));
						newRsrcs.add(rsrc);
					} else
						preExisting++;
					resources.put(line.getValue1(), rsrc);
				}
			}
			if (!newRsrcs.isEmpty()) {
				theResourceRepo.saveAll(newRsrcs);
				for (PointResource rsrc : newRsrcs)
					theResourceSvc.resourceUpdated(rsrc);
			}
			System.out.println("\t\t" + newRsrcs.size() + " added, " + preExisting + " pre-existing");

			System.out.println("\tImporting Point History...");
			System.out.println("\t\tCaching existing...");
			Set<PointHistoryKey> existingHistory = theHistoryRepo.keysByOrganization(org.getId())//
				.map(PointHistoryKey::new)//
				.collect(Collectors.toSet());

			Map<String, PointChangeRecord.PointChangeType> pcts = new HashMap<>();
			for (var pct : PointChangeRecord.PointChangeType.values())
				pcts.put(pct.name().toLowerCase(), pct);
			pcts.put("redemption", PointChangeRecord.PointChangeType.Resource);
			Set<String> unrecognizedPCTs = new HashSet<>();
			preExisting = 0;
			int addedPCRs = 0;
			List<PointChangeRecord> newPCRs = new ArrayList<>();
			System.out.println("\t\tParsing data...");
			try (CsvParser historyFile = new CsvParser(new InputStreamReader(archive.at("PointHistory.csv").read(), StandardCharsets.UTF_8),
				',', 0)) {
				var typedParser = historyFile.parseTyped()//
					.with("worker", false, s -> Long.valueOf(s))//
					.with("time", false, s -> TimeUtils.parseInstant(s, true, true, teo -> teo.localTime()).evaluate(Instant::now))//
					.with("changeType", false, s -> s)//
					.with("changeSourceId", false, s -> Long.valueOf(s))//
					.with("changeSourceName", false, s -> s)//
					.with("beforePoints", false, s -> Long.valueOf(s))//
					.with("pointChange", false, s -> Integer.valueOf(s))//
					.with("quantity", false, s -> Double.valueOf(s))//
				;
				for (var line = typedParser.parseNextLine(); line != null; line = typedParser.parseNextLine()) {
					Membership worker = workers.get(line.getValue1());
					if (worker == null) {
						System.err.println("Unrecognized worker with ID " + line.getValue1());
						continue;
					}
					Instant time = line.getValue2();
					PointChangeRecord.PointChangeType changeType = pcts.get(line.getValue3().toLowerCase());
					if (changeType == null) {
						if (unrecognizedPCTs.add(line.getValue3().toLowerCase()))
							System.err.println("Unrecognized point change type: " + line.getValue3());
						continue;
					}
					long changeSourceId;
					switch (changeType) {
					case Job:
						Job job = jobs.get(line.get(3, Long.class));
						if (job != null)
							changeSourceId = job.getId();
						else
							changeSourceId = -1;
						break;
					case Resource:
						PointResource rsrc = resources.get(line.get(3, Long.class));
						if (rsrc != null)
							changeSourceId = rsrc.getId();
						else
							changeSourceId = -1;
						break;
					default:
						System.err.println("Unhandled change type: " + changeType);
						continue;
					}

					if (existingHistory
						.contains(new PointHistoryKey(worker.getMember().getId(), time, changeType, changeSourceId))) {
						preExisting++;
						continue;
					}
					addedPCRs++;
					PointChangeRecord pcr = new PointChangeRecord(worker, time, changeType, changeSourceId, line.get(4, String.class),
						line.get(5, long.class), line.get(6, int.class), line.get(7, double.class), 1);
					newPCRs.add(pcr);
					if (newPCRs.size() >= 100) {
						theHistorySvc.historyAdded(newPCRs);
						newPCRs.clear();
					}
				}
			}
			if (!newPCRs.isEmpty()) {
				theHistorySvc.historyAdded(newPCRs);
				newPCRs.clear();
			}
			System.out.println("\t\t" + addedPCRs + " added, " + preExisting + " pre-existing");

			preExisting = 0;
			List<Assignment> newAssns = new ArrayList<>();
			System.out.println("\tImporting assignments...");
			try (CsvParser assnFile = new CsvParser(new InputStreamReader(archive.at("AssignedJob.csv").read(), StandardCharsets.UTF_8),
				',', 0)) {
				var typedParser=assnFile.parseTyped()//
					.with("worker", false, s->Long.valueOf(s))//
					.with("job", false, s->Long.valueOf(s))//
					.with("completion", false, s -> "null".equals(s) ? 0 : Integer.valueOf(s))//
					;
				for (var line = typedParser.parseNextLine(); line != null; line = typedParser.parseNextLine()) {
					Membership worker = workers.get(line.getValue1());
					if (worker == null) {
						System.err.println("Unrecognized worker with ID " + line.getValue1());
						continue;
					}
					Job job=jobs.get(line.getValue2());
					if(job==null) {
						System.err.println("Unrecognized job with ID " + line.getValue2());
						continue;
					}
					Assignment assn = theAssnRepo.getByJobAndWorker(job, worker.getMember());
					boolean newAssn = assn == null;
					if (assn == null) {
						assn = new Assignment(job, worker.getMember());
						assn.setCompleted(line.getValue3());
						newAssns.add(assn);
					} else
						preExisting++;
				}
			}
			if (!newAssns.isEmpty()) {
				theAssnRepo.saveAll(newAssns);
				for (Assignment assn : newAssns)
					theWorkSvc.assignmentUpdated(assn);
			}
			System.out.println("\t\t" + newAssns.size() + " added, " + preExisting + " pre-existing");

		} catch (IOException e) {
			System.err.println("Error accessing uploaded data");
			e.printStackTrace();
		} catch (TextParseException e) {
			System.err.println("Error parsing uploaded data");
			e.printStackTrace();
		}
	}

	static class PointHistoryKey {
		final long workerId;
		final long time;
		final PointChangeRecord.PointChangeType changeType;
		final long changeSourceId;

		PointHistoryKey(PointChangeRecord.PcrKeyDto dto) {
			this.workerId = dto.workerId();
			this.time = dto.time().toEpochMilli();
			this.changeType = dto.changeType();
			this.changeSourceId = dto.changeSourceId();
		}

		PointHistoryKey(long workerId, Instant time, PointChangeType changeType, long changeSourceId) {
			this.workerId = workerId;
			this.time = time.toEpochMilli();
			this.changeType = changeType;
			this.changeSourceId = changeSourceId;
		}

		@Override
		public int hashCode() {

			return Objects.hash(workerId, time, changeType, changeSourceId);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof PointHistoryKey))
				return false;
			PointHistoryKey other = (PointHistoryKey) obj;
			return workerId == other.workerId //
				&& time == other.time//
				&& changeType.equals(other.changeType)//
				&& changeSourceId == other.changeSourceId;
		}
	}
}
