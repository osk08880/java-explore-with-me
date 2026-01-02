package ru.practicum.explorewithme.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.explorewithme.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.explorewithme.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.explorewithme.request.dto.ParticipationRequestDto;
import ru.practicum.explorewithme.server.entity.Event;
import ru.practicum.explorewithme.server.entity.EventState;
import ru.practicum.explorewithme.server.entity.Request;
import ru.practicum.explorewithme.server.entity.RequestStatus;
import ru.practicum.explorewithme.server.exception.EntityNotFoundException;
import ru.practicum.explorewithme.server.repository.EventRepository;
import ru.practicum.explorewithme.server.repository.RequestRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestService {

    private final RequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserService userService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String REQUEST_NOT_FOUND = "Request not found";
    private static final String NOT_YOUR_REQUEST = "This is not your request";
    private static final String NOT_YOUR_EVENT = "This is not your event";
    private static final String LIMIT_REACHED = "Participant limit reached";

    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {
        userService.getById(userId);

        Event event = eventRepository.findByIdWithInitiator(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getInitiator().getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot request participation in your own event");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new IllegalArgumentException("Event is not published");
        }

        Long confirmedCount = getConfirmedCount(eventId);
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new IllegalArgumentException(LIMIT_REACHED);
        }

        Request request = Request.builder()
                .event(event)
                .requester(userService.getById(userId))
                .status(event.getRequestModeration() ? RequestStatus.PENDING : RequestStatus.CONFIRMED)
                .created(LocalDateTime.now())
                .build();

        request = requestRepository.save(request);
        entityManager.flush();
        return toDto(request);
    }

    @Transactional
    public ParticipationRequestDto cancel(Long userId, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request with id=" + requestId + " was not found"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new IllegalArgumentException(NOT_YOUR_REQUEST);
        }

        request.setStatus(RequestStatus.CANCELED);
        requestRepository.save(request);
        entityManager.flush();

        return toDto(request);
    }

    public List<ParticipationRequestDto> getByUser(Long userId) {
        userService.getById(userId);
        List<Request> requests = requestRepository.findAllByRequesterId(userId);
        return requests.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ParticipationRequestDto> getByEvent(Long userId, Long eventId) {
        userService.getById(userId);

        Event event = eventRepository.findByIdWithInitiator(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event with id=" + eventId + " was not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new IllegalArgumentException(NOT_YOUR_EVENT);
        }

        List<Request> requests = requestRepository.findAllByEventId(eventId);
        return requests.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult changeStatus(Long userId, Long eventId, EventRequestStatusUpdateRequest update) {
        userService.getById(userId);

        Event event = eventRepository.findByIdWithInitiator(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event with id=" + eventId + " was not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new IllegalArgumentException(NOT_YOUR_EVENT);
        }

        List<Request> requests = update.getRequestIds().stream()
                .map(id -> requestRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Request with id=" + id + " was not found")))
                .filter(r -> r.getStatus() == RequestStatus.PENDING)
                .collect(Collectors.toList());

        Long confirmedCount = getConfirmedCount(eventId);
        if ("CONFIRMED".equals(update.getStatus()) && event.getParticipantLimit() > 0
                && confirmedCount + requests.size() > event.getParticipantLimit()) {
            throw new IllegalArgumentException(LIMIT_REACHED);
        }

        List<Request> confirmed = new ArrayList<>();
        List<Request> rejected = new ArrayList<>();

        for (Request r : requests) {
            if ("CONFIRMED".equals(update.getStatus())) {
                r.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(r);
            } else {
                r.setStatus(RequestStatus.REJECTED);
                rejected.add(r);
            }
        }

        requestRepository.saveAll(requests);
        entityManager.flush();

        List<ParticipationRequestDto> additionalRejected = new ArrayList<>();
        if ("CONFIRMED".equals(update.getStatus()) && event.getParticipantLimit() > 0
                && confirmedCount + confirmed.size() >= event.getParticipantLimit()) {
            List<Request> remainingPending = requestRepository.findAllByEventIdAndStatus(eventId, RequestStatus.PENDING);
            remainingPending.forEach(r -> r.setStatus(RequestStatus.REJECTED));
            requestRepository.saveAll(remainingPending);
            entityManager.flush();
            additionalRejected = remainingPending.stream().map(this::toDto).collect(Collectors.toList());
        }

        List<ParticipationRequestDto> confirmedDtos = confirmed.stream().map(this::toDto).collect(Collectors.toList());
        List<ParticipationRequestDto> rejectedDtos = rejected.stream().map(this::toDto).collect(Collectors.toList());
        rejectedDtos.addAll(additionalRejected);

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmedDtos)
                .rejectedRequests(rejectedDtos)
                .build();
    }

    public Long getConfirmedCount(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    private ParticipationRequestDto toDto(Request request) {
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .created(request.getCreated())
                .event(request.getEvent().getId())
                .requester(request.getRequester().getId())
                .status(request.getStatus().name())
                .build();
    }
}
