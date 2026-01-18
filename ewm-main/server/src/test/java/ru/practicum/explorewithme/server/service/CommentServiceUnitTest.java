package ru.practicum.explorewithme.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import ru.practicum.explorewithme.comment.dto.NewCommentDto;
import ru.practicum.explorewithme.comment.dto.CommentDto;
import ru.practicum.explorewithme.server.entity.Comment;
import ru.practicum.explorewithme.server.entity.Event;
import ru.practicum.explorewithme.server.entity.EventState;
import ru.practicum.explorewithme.server.entity.User;
import ru.practicum.explorewithme.server.exception.EntityNotFoundException;
import ru.practicum.explorewithme.server.mapper.CommentMapper;
import ru.practicum.explorewithme.server.repository.CommentRepository;
import ru.practicum.explorewithme.server.repository.EventRepository;
import ru.practicum.explorewithme.server.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentService commentService;

    @Test
    void testCreateComment() {
        Long userId = 1L;
        Long eventId = 1L;
        NewCommentDto dto = new NewCommentDto("Test text");
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setName("Test User");
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        mockEvent.setState(EventState.PUBLISHED);
        Comment mockComment = new Comment();
        mockComment.setId(2L);
        CommentDto mockDto = new CommentDto();
        mockDto.setId(2L);
        mockDto.setText("Test text");
        mockDto.setAuthorId(userId);
        mockDto.setAuthorName("Test User");
        mockDto.setEventId(eventId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(mockEvent));
        when(commentMapper.toEntity(dto, mockUser, mockEvent)).thenReturn(mockComment);
        when(commentRepository.save(mockComment)).thenReturn(mockComment);
        when(commentMapper.toDto(mockComment)).thenReturn(mockDto);

        // Act
        CommentDto result = commentService.createComment(userId, eventId, dto);

        // Assert
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getText()).isEqualTo("Test text");
        assertThat(result.getAuthorId()).isEqualTo(userId);
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getAuthorName()).isEqualTo("Test User");
        verify(commentRepository, times(1)).save(any(Comment.class));
        verify(commentMapper, times(1)).toDto(mockComment);
    }

    @Test
    void testCreateCommentEventNotPublished() {
        Long userId = 1L;
        Long eventId = 1L;
        NewCommentDto dto = new NewCommentDto("Test text");
        User mockUser = new User();
        mockUser.setId(userId);
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        mockEvent.setState(EventState.PENDING);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(mockEvent));

        assertThatThrownBy(() -> commentService.createComment(userId, eventId, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Комментарии возможны только для опубликованных событий");
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testCreateCommentUserNotFound() {
        // Arrange
        Long userId = 999L;
        Long eventId = 1L;
        NewCommentDto dto = new NewCommentDto("Test text");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> commentService.createComment(userId, eventId, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Пользователь с id=999 не найден");

        verify(userRepository, times(1)).findById(userId);
        verify(eventRepository, never()).findById(any());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testCreateCommentEventNotFound() {
        Long userId = 1L;
        Long eventId = 999L;
        NewCommentDto dto = new NewCommentDto("Test text");
        User mockUser = new User();
        mockUser.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(userId, eventId, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Событие с id=999 не найдено");
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testUpdateComment() {
        Long eventId = 1L;
        Long commentId = 1L;
        Long userId = 1L;
        NewCommentDto dto = new NewCommentDto("Updated text");
        User mockUser = new User();
        mockUser.setId(userId);
        Comment mockComment = new Comment();
        mockComment.setId(commentId);
        mockComment.setAuthor(mockUser);
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        mockComment.setEvent(mockEvent);
        CommentDto mockDto = new CommentDto();
        mockDto.setId(commentId);
        mockDto.setText("Updated text");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));
        when(commentMapper.toDto(mockComment)).thenReturn(mockDto);
        when(commentRepository.save(mockComment)).thenReturn(mockComment);

        CommentDto result = commentService.updateComment(eventId, commentId, userId, dto);

        assertThat(result.getId()).isEqualTo(commentId);
        assertThat(result.getText()).isEqualTo("Updated text");
        verify(commentRepository, times(1)).save(mockComment);
    }

    @Test
    void testUpdateCommentAccessDenied() {
        Long eventId = 1L;
        Long commentId = 1L;
        Long userId = 2L;
        NewCommentDto dto = new NewCommentDto("Unauthorized update");
        User mockUser = new User();
        mockUser.setId(userId);
        User mockAuthor = new User();
        mockAuthor.setId(1L);
        Comment mockComment = new Comment();
        mockComment.setId(commentId);
        mockComment.setAuthor(mockAuthor);
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        mockComment.setEvent(mockEvent);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));

        assertThatThrownBy(() -> commentService.updateComment(eventId, commentId, userId, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Доступ запрещён: только автор комментария может редактировать его");

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testUpdateCommentCommentNotFound() {
        Long eventId = 1L;
        Long commentId = 999L;
        Long userId = 1L;
        NewCommentDto dto = new NewCommentDto("Update");

        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.updateComment(eventId, commentId, userId, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Комментарий с id=999 не найден");
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testGetCommentsByEvent() {
        Long eventId = 1L;
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        List<Comment> mockComments = List.of(new Comment(), new Comment());
        List<CommentDto> mockDtos = List.of(new CommentDto(), new CommentDto());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(mockEvent));
        when(commentRepository.findAllByEventId(eq(eventId), any(PageRequest.class))).thenReturn(Page.empty());
        when(commentMapper.toDtos(any())).thenReturn(mockDtos);

        List<CommentDto> result = commentService.getCommentsByEvent(eventId, 0, 10);

        assertThat(result).isEqualTo(mockDtos);
        verify(commentRepository, times(1)).findAllByEventId(eq(eventId), any(PageRequest.class));
    }

    @Test
    void testGetCommentsByEventNotFound() {
        Long eventId = 999L;

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getCommentsByEvent(eventId, 0, 10))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Событие с id=999 не найдено");
        verify(commentRepository, never()).findAllByEventId(any(), any());
    }

    @Test
    void testDeleteCommentByAuthor() {
        Long eventId = 1L;
        Long commentId = 1L;
        Long userId = 1L;
        User mockUser = new User();
        mockUser.setId(userId);
        Comment mockComment = new Comment();
        mockComment.setId(commentId);
        mockComment.setAuthor(mockUser);
        mockComment.setEvent(new Event());
        mockComment.getEvent().setId(eventId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));

        commentService.deleteComment(eventId, commentId, userId);

        verify(commentRepository, times(1)).delete(mockComment);
    }

    @Test
    void testDeleteCommentByAdmin() {
        Long eventId = 1L;
        Long commentId = 1L;
        Long adminId = 1L;
        User mockUser = new User();
        mockUser.setId(adminId);
        Comment mockComment = new Comment();
        mockComment.setId(commentId);
        mockComment.setAuthor(new User());
        mockComment.setEvent(new Event());
        mockComment.getEvent().setId(eventId);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(mockUser));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));

        commentService.deleteComment(eventId, commentId, adminId);

        verify(commentRepository, times(1)).delete(mockComment);
    }

    @Test
    void testDeleteCommentAccessDenied() {
        Long eventId = 1L;
        Long commentId = 1L;
        Long userId = 2L;
        User mockUser = new User();
        mockUser.setId(userId);
        User mockAuthor = new User();
        mockAuthor.setId(1L);
        Comment mockComment = new Comment();
        mockComment.setId(commentId);
        mockComment.setAuthor(mockAuthor);
        Event mockEvent = new Event();
        mockEvent.setId(eventId);
        mockComment.setEvent(mockEvent);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(mockComment));

        assertThatThrownBy(() -> commentService.deleteComment(eventId, commentId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Доступ запрещён: только автор комментария или администратор может выполнить операцию");

        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void testDeleteCommentCommentNotFound() {
        Long eventId = 1L;
        Long commentId = 999L;
        Long userId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(eventId, commentId, userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Комментарий с id=999 не найден");
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void testGetCommentCount() {
        Long eventId = 1L;
        Long expectedCount = 5L;

        when(commentRepository.countByEventId(eventId)).thenReturn(expectedCount);

        Long result = commentService.getCommentCount(eventId);

        assertThat(result).isEqualTo(expectedCount);
        verify(commentRepository, times(1)).countByEventId(eventId);
    }
}