package org.quark.misc.choresweb.ctl;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;

import org.qommons.TableContentControl;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.ParsedInstant;
import org.quark.misc.choresweb.api.ApiUser;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.OrgsRepo;
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.svc.ApplicationInfoService;
import org.quark.misc.choresweb.svc.BulkUserModification;
import org.quark.misc.choresweb.svc.GodService;
import org.quark.misc.choresweb.svc.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/god")
public class GodViewController {
	public static class BulkQueryResult<T> {
		public final long resultCount;
		public final List<T> pageResults;

		public BulkQueryResult(long resultCount, List<T> pageResults) {
			this.resultCount = resultCount;
			this.pageResults = pageResults;
		}
	}

	private final UserService theUserService;
	private final ApplicationInfoService theAppInfoService;
	private final OrgsRepo theOrgsRepo;
	private final UserRepo theUserRepo;
	private final GodService theGodService;

	public GodViewController(UserService userService, ApplicationInfoService appInfoService, OrgsRepo orgsRepo, UserRepo userRepo,
		GodService godService) {
		theUserService = userService;
		theAppInfoService = appInfoService;
		theOrgsRepo = orgsRepo;
		theUserRepo = userRepo;
		theGodService = godService;
	}

	@GetMapping("/org-count")
	public int getOrganizationCount(@AuthenticationPrincipal Jwt user) {
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		return theOrgsRepo.getOrganizationCount();
	}

	@GetMapping("/user-count")
	public int getUserCount(@AuthenticationPrincipal Jwt user) {
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		return theUserRepo.getUserCount();
	}

	@GetMapping("/users")
	public BulkQueryResult<ApiUser> getUsers(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = false) String query, //
		@RequestParam(required = true) int pageNumber, //
		@RequestParam(required = true) int pageSize) {
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		Specification<User> hqlQuery;
		Sort sort;
		if(query!=null && !query.isEmpty()) {
			TableContentControl parsedFilter;
			try {
				parsedFilter = TableContentControl.parseContentControl(query);
			} catch (ParseException | RuntimeException e) {
				throw new IllegalArgumentException("Unrecognized user query: " + query + ": " + e.getMessage());
			}

			hqlQuery = parseUserQuery(parsedFilter);
			if (parsedFilter.getRowSorting() == null || parsedFilter.getRowSorting().isEmpty())
				sort = Sort.by("email");
			else {
				sort = Sort.unsorted();
				for (String cat : parsedFilter.getRowSorting()) {
					boolean desc = cat.charAt(0) == '-';
					if (desc)
						cat = cat.substring(1).trim();
					switch (cat.toLowerCase()) {
					case "email":
					case "name":
					case "god":
						break;
					case "lastactive":
						cat = "lastActive";
						break;
					case "globaladmin":
						cat = "globalAdmin";
						break;
					default:
						throw new IllegalArgumentException("Unrecognized sort field: " + cat);
					}
					Sort newSort = Sort.by(cat);
					if (desc)
						newSort = newSort.descending();
					sort = sort.and(newSort);
				}
			}
		} else {
			hqlQuery=Specification.unrestricted();
			sort = Sort.by("email");
		}

		Pageable page = PageRequest.of(pageNumber, pageSize, sort);
		Page<User> result = theUserRepo.findAll(hqlQuery, page);
		return new BulkQueryResult<>(result.getTotalElements(), result.get()//
			.map(ApiUser::of)//
			.toList());
	}

	private Specification<User> parseUserQuery(TableContentControl query) {
		if (query instanceof TableContentControl.RowSorter)
			return Specification.unrestricted();
		else if (query instanceof TableContentControl.ParenFilter)
			return parseUserQuery(((TableContentControl.ParenFilter) query).getWrapped());
		else if(query instanceof TableContentControl.NotFilter)
			return Specification.not(parseUserQuery(((TableContentControl.NotFilter) query).getWrapped()));
		else if(query instanceof TableContentControl.ComponentizedControl) {
			TableContentControl.ComponentizedControl cc=(TableContentControl.ComponentizedControl) query;
			Specification<User> [] components=new Specification[cc.getComponents().size()];
			int c=0;
			for(TableContentControl component : ((TableContentControl.ComponentizedControl) query).getComponents())
				components[c++]=parseUserQuery(component);
			if(cc instanceof TableContentControl.AndFilter)
				return Specification.allOf(components);
			else
				return Specification.anyOf(components);
		} else if(query instanceof TableContentControl.CategoryFilter) {
			TableContentControl.CategoryFilter catQuery=(TableContentControl.CategoryFilter) query;
			switch(catQuery.getCategory().toLowerCase()) {
			case "email":
			case "name":
				return parseUserQuery(catQuery.getFilter(), catQuery.getCategory(), String.class);
			case "lastactive":
				return parseUserQuery(catQuery.getFilter(), catQuery.getCategory(), Instant.class);
			case "god":
			case "globaladmin":
				return parseUserQuery(catQuery.getFilter(), catQuery.getCategory(), boolean.class);
			default:
				throw new IllegalArgumentException("Unrecognized user filter category: "+catQuery.getCategory());
			}
		} else
			throw new IllegalArgumentException("Query components must be categorized.  E.g. \"email:*@gmail.com\"");
	}

	private Specification<User> parseUserQuery(TableContentControl query, String categoryName, Class<?> fieldType) {
		if(query instanceof TableContentControl.ParenFilter)
			return parseUserQuery(((TableContentControl.ParenFilter) query).getWrapped(), categoryName, fieldType);
		else if(query instanceof TableContentControl.NotFilter)
			return Specification.not(parseUserQuery(((TableContentControl.NotFilter) query).getWrapped(), categoryName, fieldType));
		else if (query instanceof TableContentControl.QuotedFilter)
			return parseUserQuery(((TableContentControl.QuotedFilter) query).getWrapped(), categoryName, fieldType);
		else if(query instanceof TableContentControl.ComponentizedControl) {
			TableContentControl.ComponentizedControl cc=(TableContentControl.ComponentizedControl) query;
			Specification<User> [] components=new Specification[cc.getComponents().size()];
			int c=0;
			for(TableContentControl component : ((TableContentControl.ComponentizedControl) query).getComponents())
				components[c++]=parseUserQuery(component, categoryName, fieldType);
			if(cc instanceof TableContentControl.AndFilter)
				return Specification.allOf(components);
			else
				return Specification.anyOf(components);
		} else if(query instanceof TableContentControl.DateFilter) {
			if(fieldType!=Instant.class)
				throw new IllegalArgumentException("Cannot apply a date filter to "+fieldType.getSimpleName()+" field "+categoryName);
			ParsedInstant time=((TableContentControl.DateFilter) query).getTime();
			Instant now=Instant.now();
			Instant min=time.getMin(()->now, TimeUtils.RelativeInstantEvaluation.Past);
			Instant max=time.getMax(()->now, TimeUtils.RelativeInstantEvaluation.Past);
			return Specification.allOf(//
				UserRepo.lastActive(true, true, min),//
				UserRepo.lastActive(false, false, max));
		} else if(query instanceof TableContentControl.DateRangeFilter) {
			if (fieldType != Instant.class)
				throw new IllegalArgumentException(
					"Cannot apply a date filter to " + fieldType.getSimpleName() + " field " + categoryName + ": " + query);
			Instant now = Instant.now();
			Instant min = ((TableContentControl.DateRangeFilter) query).getMinTime().getMin(() -> now,
				TimeUtils.RelativeInstantEvaluation.Past);
			Instant max = ((TableContentControl.DateRangeFilter) query).getMaxTime().getMax(() -> now,
				TimeUtils.RelativeInstantEvaluation.Past);
			return Specification.allOf(//
				UserRepo.lastActive(true, true, min), //
				UserRepo.lastActive(false, false, max));
		} else if(query instanceof TableContentControl.SimpleFilter) {
			String text = query.toString();
			String pattern = text.replace('*', '%');
			switch (categoryName.toLowerCase()) {
			case "email":
				return pattern.equals(text) ? UserRepo.emailEqual(text) : UserRepo.emailLike(pattern);
			case "name":
				return pattern.equals(text) ? UserRepo.nameEqual(text) : UserRepo.nameLike(pattern);
			default:
				throw new IllegalArgumentException(
					"Cannot apply a quoted filter to " + fieldType.getSimpleName() + " field " + categoryName + ": " + query);
			}
		} else
			throw new IllegalArgumentException("Unrecognized or unhandled query type: " + query);
	}

	@PostMapping("/users")
	public void modifyUsers(@AuthenticationPrincipal Jwt user,//
		@RequestParam(required = false) String query, //
		@RequestParam(required = false) List<Long> selectedUserIds, //
		@RequestBody BulkUserModification modification) {
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		if (selectedUserIds != null) {
			theGodService.modifyUsers(selectedUserIds, modification);
			return;
		}

		Specification<User> hqlQuery;
		if(query!=null && !query.isEmpty()) {
			TableContentControl parsedFilter;
			try {
				parsedFilter = TableContentControl.parseContentControl(query);
			} catch (ParseException | RuntimeException e) {
				throw new IllegalArgumentException("Unrecognized user query: " + query + ": " + e.getMessage());
			}

			hqlQuery = parseUserQuery(parsedFilter);
		} else {
			hqlQuery=Specification.unrestricted();
		}
		theGodService.modifyUsers(hqlQuery, modification);
	}

	@DeleteMapping("/users")
	public void deleteUsers(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = false) String query, //
		@RequestParam(required = false) List<Long> selectedUserIds) {
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		if (selectedUserIds != null) {
			theGodService.deleteUsers(selectedUserIds);
			return;
		}

		Specification<User> hqlQuery;
		if (query != null && !query.isEmpty()) {
			TableContentControl parsedFilter;
			try {
				parsedFilter = TableContentControl.parseContentControl(query);
			} catch (ParseException | RuntimeException e) {
				throw new IllegalArgumentException("Unrecognized user query: " + query + ": " + e.getMessage());
			}

			hqlQuery = parseUserQuery(parsedFilter);
		} else {
			hqlQuery = Specification.unrestricted();
		}
		theGodService.deleteUsers(hqlQuery);
	}

	@GetMapping("/open-to-world")
	public boolean isOpenToWorld(@AuthenticationPrincipal Jwt user) {
		// This information is available to all users, but we'll make them get it through the normal services
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		return theAppInfoService.getApplicationInfo().isOpenToWorld();
	}

	@PutMapping("/open-to-world/{open}")
	public void setOpenToWorked(@AuthenticationPrincipal Jwt user, @PathVariable boolean open) {
		User me = theUserService.getMe(user);
		if (me == null || !me.isGod())
			throw new UnsupportedOperationException("You do not have access to this service");

		theGodService.setOpenToWorld(open);
	}
}
