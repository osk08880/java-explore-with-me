package ru.practicum.explorewithme.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventAdminRequest {

    @Size(max = 2000, message = "Annotation cannot exceed 2000 characters")
    private String annotation;

    private Long category;

    @Size(max = 7000, message = "Description cannot exceed 7000 characters")
    private String description;

    @Future(message = "Event date must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;

    private Location location;

    private Boolean paid;

    @Min(value = 0, message = "Participant limit cannot be negative")
    private Integer participantLimit;

    private Boolean requestModeration;

    private String stateAction;

    @Size(max = 120, message = "Title cannot exceed 120 characters")
    private String title;
}
