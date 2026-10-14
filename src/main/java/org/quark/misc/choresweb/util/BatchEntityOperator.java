package org.quark.misc.choresweb.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.data.repository.ListCrudRepository;

import jakarta.persistence.EntityManager;

public class BatchEntityOperator<E, ID> {
	public interface Operation<E, ID> {
		/**
		 * Performs the operation. The given IDs are guaranteed to be exactly those of the given entities, but the order is not guaranteed
		 * to be the same.
		 * 
		 * @param entities The entities to operate on
		 * @param entityIds The IDs of all the entities (order may not match)
		 */
		void operate(Collection<E> entities, Collection<ID> entityIds);
	}

	private final ListCrudRepository<E, ID> theRepo;
	private final EntityManager theEntityManager;
	private final Function<? super E, ID> theIdGetter;
	private final Predicate<? super E> theFilter;

	/**
	 * @param repo The repository to operate on
	 * @param entityManager The entity manager to interact with hibernate's cache
	 * @param idGetter Gets the ID value for an entity instance
	 * @param filter An optional filter to return false for entities that should NOT be operated on by this operator
	 */
	public BatchEntityOperator(ListCrudRepository<E, ID> repo, EntityManager entityManager, Function<? super E, ID> idGetter,
		Predicate<? super E> filter) {
		theRepo = repo;
		theEntityManager = entityManager;
		theIdGetter = idGetter;
		theFilter = filter;
	}

	public int operate(List<ID> entityIds, Operation<E, ID> operation, int batchSize) {
		if (entityIds.isEmpty())
			return 0;
		int total = 0;
		for (int i = 0; i < entityIds.size(); i += batchSize) {
			Collection<ID> batchIds = entityIds.subList(i, Math.min(i + batchSize, entityIds.size()));
			List<E> batchEntities = theRepo.findAllById(batchIds);

			boolean batchDataCopied = false;
			for (int j = 0; j < batchEntities.size(); j++) {
				E entity = batchEntities.get(j);
				if (!theFilter.test(entity)) { // No modification on God eternal
					if (!batchDataCopied) {
						batchIds = new HashSet<>(batchIds);
						batchEntities = new ArrayList<>(batchEntities);
					}
					batchIds.remove(theIdGetter.apply(entity));
					batchEntities.remove(j);
					j--; // Set the counter back since we moved future elements in the list backward
				}
			}

			total += batchIds.size();
			operation.operate(//
				Collections.unmodifiableList(batchEntities), Collections.unmodifiableCollection(batchIds));

			theEntityManager.flush();
			theEntityManager.clear();
		}
		return total;
	}
}
