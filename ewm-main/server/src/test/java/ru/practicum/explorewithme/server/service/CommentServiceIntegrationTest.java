package ru.practicum.explorewithme.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.explorewithme.comment.dto.NewCommentDto;
import ru.practicum.explorewithme.comment.dto.CommentDto;
import ru.practicum.explorewithme.server.entity.*;
import ru.practicum.explorewithme.server.exception.EntityNotFoundException;
import ru.practicum.explorewithme.server.repository.CategoryRepository;
import ru.practicum.explorewithme.server.repository.CommentRepository;
import ru.practicum.explorewithme.server.repository.EventRepository;
import ru.practicum.explorewithme.server.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommentServiceIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CommentRepository commentRepository;

    private User testUser;
    private User adminUser;
    private Category testCategory;
    private Event publishedEvent;
    private Event pendingEvent;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setName("Admin");
        adminUser.setEmail("admin@example.com");
        adminUser = userRepository.save(adminUser);

        testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser = userRepository.save(testUser);

        testCategory = new Category();
        testCategory.setName("Test Category");
        testCategory = categoryRepository.save(testCategory);

        publishedEvent = createTestEvent(testUser, testCategory, EventState.PUBLISHED);

        pendingEvent = createTestEvent(testUser, testCategory, EventState.PENDING);
    }

    private Event createTestEvent(User initiator, Category category, EventState state) {
        Event event = new Event();
        event.setTitle("Test Event");
        event.setAnnotation("Test Annotation");
        event.setDescription("Test Description");
        event.setEventDate(LocalDateTime.now().plusDays(1));
        event.setCreatedOn(LocalDateTime.now());
        event.setState(state);
        event.setInitiator(initiator);
        event.setCategory(category);
        event.setPaid(false);
        event.setRequestModeration(true);
        return eventRepository.save(event);
    }

    @Test
    void testCreateComment() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Это тестовый комментарий");

        CommentDto commentDto = commentService.createComment(testUser.getId(), publishedEvent.getId(), newComment);

        assertThat(commentDto).isNotNull();
        assertThat(commentDto.getText()).isEqualTo("Это тестовый комментарий");
        assertThat(commentDto.getEventId()).isEqualTo(publishedEvent.getId());
        assertThat(commentDto.getAuthorId()).isEqualTo(testUser.getId());
        assertThat(commentDto.getCreatedAt()).isNotNull();
        assertThat(commentDto.getAuthorName()).isEqualTo(testUser.getName());

        assertThat(commentRepository.findById(commentDto.getId())).isPresent();
    }

    @Test
    void testCreateCommentOnUnpublishedEvent() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Комментарий к unpublished event");

        assertThatThrownBy(() -> commentService.createComment(testUser.getId(), pendingEvent.getId(), newComment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Комментарии возможны только для опубликованных событий");
    }

    @Test
    void testUpdateComment() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Original text");
        CommentDto createdComment = commentService.createComment(testUser.getId(), publishedEvent.getId(), newComment);

        NewCommentDto updateComment = new NewCommentDto();
        updateComment.setText("Updated text");

        CommentDto updatedComment = commentService.updateComment(publishedEvent.getId(), createdComment.getId(), testUser.getId(), updateComment);

        assertThat(updatedComment.getText()).isEqualTo("Updated text");
        assertThat(updatedComment.getId()).isEqualTo(createdComment.getId());
        assertThat(updatedComment.getAuthorId()).isEqualTo(testUser.getId());

        Comment savedComment = commentRepository.findById(updatedComment.getId()).orElse(null);
        assertThat(savedComment).isNotNull();
        assertThat(savedComment.getText()).isEqualTo("Updated text");
    }

    @Test
    void testUpdateCommentAccessDenied() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Comment by testUser");
        CommentDto createdComment = commentService.createComment(testUser.getId(), publishedEvent.getId(), newComment);

        NewCommentDto updateComment = new NewCommentDto();
        updateComment.setText("Unauthorized update");

        assertThatThrownBy(() -> commentService.updateComment(publishedEvent.getId(), createdComment.getId(), adminUser.getId(), updateComment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Доступ запрещён: только автор комментария может редактировать его");
    }

    @Test
    void testGetCommentsByEvent() {
        NewCommentDto comment1 = new NewCommentDto();
        comment1.setText("Comment 1");
        commentService.createComment(testUser.getId(), publishedEvent.getId(), comment1);

        NewCommentDto comment2 = new NewCommentDto();
        comment2.setText("Comment 2");
        commentService.createComment(testUser.getId(), publishedEvent.getId(), comment2);

        NewCommentDto comment3 = new NewCommentDto();
        comment3.setText("Comment 3");
        commentService.createComment(testUser.getId(), publishedEvent.getId(), comment3);

        List<CommentDto> comments = commentService.getCommentsByEvent(publishedEvent.getId(), 1, 2);

        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).getText()).isIn("Comment 1", "Comment 2", "Comment 3");
        assertThat(comments.get(1).getText()).isIn("Comment 1", "Comment 2", "Comment 3");

        List<CommentDto> allComments = commentService.getCommentsByEvent(publishedEvent.getId(), 0, 10);
        assertThat(allComments).hasSize(3);
    }

    @Test
    void testGetCommentsByEventEmptyList() {
        List<CommentDto> comments = commentService.getCommentsByEvent(publishedEvent.getId(), 0, 10);

        assertThat(comments).isEmpty();
    }

    @Test
    void testDeleteCommentByAuthor() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Comment to delete");
        CommentDto createdComment = commentService.createComment(testUser.getId(), publishedEvent.getId(), newComment);

        commentService.deleteComment(publishedEvent.getId(), createdComment.getId(), testUser.getId());

        assertThat(commentRepository.findById(createdComment.getId())).isEmpty();
    }

    @Test
    void testDeleteCommentAccessDenied() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Comment by testUser");
        CommentDto createdComment = commentService.createComment(testUser.getId(), publishedEvent.getId(), newComment);

        assertThatThrownBy(() -> commentService.deleteComment(publishedEvent.getId(), createdComment.getId(), adminUser.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Доступ запрещён: только автор комментария или администратор может выполнить операцию");
    }

    @Test
    void testDeleteCommentByAdmin() {
        NewCommentDto newComment = new NewCommentDto();
        newComment.setText("Comment by testUser for admin delete");
        CommentDto createdComment = commentService.createComment(testUser.getId(), publishedEvent.getId(), newComment);

        commentService.deleteComment(publishedEvent.getId(), createdComment.getId(), 1L);

        assertThat(commentRepository.findById(createdComment.getId())).isEmpty();
    }

    @Test
    void testDeleteCommentNonExistent() {
        assertThatThrownBy(() -> commentService.deleteComment(publishedEvent.getId(), 999L, testUser.getId()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Комментарий с id=999 не найден");
    }
}