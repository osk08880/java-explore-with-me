package ru.practicum.explorewithme.server.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.explorewithme.compilation.dto.CompilationDto;
import ru.practicum.explorewithme.compilation.dto.NewCompilationDto;
import ru.practicum.explorewithme.compilation.dto.UpdateCompilationRequest;
import ru.practicum.explorewithme.server.repository.CompilationRepository;
import ru.practicum.explorewithme.server.entity.Compilation;
import ru.practicum.explorewithme.server.entity.Event;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventService eventService;
    @PersistenceContext
    private EntityManager entityManager;

    private static final String COMPILATION_NOT_FOUND = "Подборка не найдена";
    private static final boolean DEFAULT_PINNED = false;

    @Transactional
    public CompilationDto create(NewCompilationDto newCompilation) {
        log.info("Создание подборки с заголовком '{}'", newCompilation.getTitle());
        Compilation compilation = Compilation.builder()
                .title(newCompilation.getTitle())
                .pinned(newCompilation.getPinned() != null ? newCompilation.getPinned() : DEFAULT_PINNED)
                .build();
        Set<Event> events = new HashSet<>();
        if (newCompilation.getEvents() != null && !newCompilation.getEvents().isEmpty()) {
            newCompilation.getEvents().forEach(eventId -> events.add(eventService.getById(eventId)));
        }
        compilation.setEvents(events);
        compilation = compilationRepository.save(compilation);
        entityManager.flush();
        log.info("Подборка создана с ID {}", compilation.getId());
        return toDto(compilation);
    }

    public CompilationDto update(Long compId, UpdateCompilationRequest update) {
        log.info("Обновление подборки ID {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new IllegalArgumentException(COMPILATION_NOT_FOUND));
        if (update.getTitle() != null) compilation.setTitle(update.getTitle());
        if (update.getPinned() != null) compilation.setPinned(update.getPinned());
        Set<Event> events = new HashSet<>();
        if (update.getEvents() != null && !update.getEvents().isEmpty()) {
            update.getEvents().forEach(eventId -> events.add(eventService.getById(eventId)));
        }
        compilation.setEvents(events);
        compilation = compilationRepository.save(compilation);
        log.info("Подборка ID {} обновлена", compId);
        return toDto(compilation);
    }

    public void delete(Long compId) {
        log.info("Удаление подборки ID {}", compId);
        compilationRepository.deleteById(compId);
        log.info("Подборка ID {} удалена", compId);
    }

    public List<CompilationDto> getAll(Boolean pinned, Integer from, Integer size) {
        log.debug("Получение подборок pinned={} from {} size {}", pinned, from, size);
        PageRequest pageable = PageRequest.of(from / size, size);
        List<Compilation> compilations;
        if (pinned != null) {
            Page<Compilation> page = compilationRepository.findAllByPinned(pinned, pageable);
            compilations = page.getContent();
        } else {
            Page<Compilation> page = compilationRepository.findAll(pageable);
            compilations = page.getContent();
        }
        return compilations.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CompilationDto getById(Long compId) {
        log.debug("Получение подборки ID {}", compId);
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new IllegalArgumentException(COMPILATION_NOT_FOUND));
        return toDto(compilation);
    }

    private CompilationDto toDto(Compilation compilation) {
        return CompilationDto.builder()
                .id(compilation.getId())
                .events(compilation.getEvents().stream().map(eventService::toShortDto).collect(Collectors.toList()))
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .build();
    }
}