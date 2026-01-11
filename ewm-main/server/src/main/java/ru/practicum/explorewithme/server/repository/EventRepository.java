package ru.practicum.explorewithme.server.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.explorewithme.server.entity.Event;
import ru.practicum.explorewithme.server.entity.EventState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByCategoryId(Long categoryId);

    @Query(value = """
SELECT * FROM events e
WHERE (:text IS NULL
       OR e.annotation ILIKE CONCAT('%', :text, '%')
       OR e.description ILIKE CONCAT('%', :text, '%')
       OR e.title ILIKE CONCAT('%', :text, '%'))
  AND (:categories IS NULL OR e.category_id = ANY(:categories))
  AND e.state = :publishedState
  AND (:paid IS NULL OR e.paid = :paid)
  AND e.event_date BETWEEN :rangeStart AND :rangeEnd
  AND (:onlyAvailable IS NULL OR e.participant_limit = 0
       OR e.participant_limit > (SELECT COUNT(r.id)
                                 FROM requests r
                                 WHERE r.event_id = e.id AND r.status='CONFIRMED'))
ORDER BY e.event_date DESC
""", nativeQuery = true)
    List<Event> findPublicEvents(
            @Param("text") String text,
            @Param("categories") List<Long> categories,
            @Param("paid") Boolean paid,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("onlyAvailable") Boolean onlyAvailable,
            @Param("publishedState") String publishedState,
            Pageable pageable
    );

    @Query("""
        SELECT e FROM Event e
        WHERE (:users IS NULL OR e.initiator.id IN :users)
          AND (:states IS NULL OR e.state IN :states)
          AND (:categories IS NULL OR e.category.id IN :categories)
          AND e.eventDate BETWEEN :rangeStart AND :rangeEnd
    """)
    List<Event> findAdminEvents(
            @Param("users") List<Long> users,
            @Param("states") List<EventState> states,
            @Param("categories") List<Long> categories,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            Pageable pageable
    );

    List<Event> findAllByInitiatorId(Long userId, Pageable pageable);

    @Query("SELECT e FROM Event e JOIN FETCH e.initiator WHERE e.id = :id")
    Optional<Event> findByIdWithInitiator(@Param("id") Long id);
}
