package com.tourlk.entity;

import com.tourlk.enums.DestinationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "destinations")
public class Destination extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String region; // Province එක සඳහා

    @Column(length = 150)
    private String district; // දිස්ත්‍රික්කය සඳහා

    @Column(length = 100)
    private String category; // වර්ගය (Beach, Historical, වගේ දේකට)

    @Column(length = 150)
    private String bestTimeToVisit; // යන්න හොඳම කාලය

    @Lob
    @Column
    private String imageUrl; // පින්තූරයක URL එක සඳහා

    @Lob
    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DestinationStatus status = DestinationStatus.ACTIVE;

}