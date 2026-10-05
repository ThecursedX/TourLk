package com.tourlk.service;

import com.tourlk.dto.AddOnRoomDto;
import com.tourlk.dto.AddOnVehicleDto;
import com.tourlk.dto.PackageAddOnResponseDto;
import com.tourlk.entity.PackageAddOn;
import com.tourlk.entity.Room;
import com.tourlk.entity.Vehicle;

final class PackageAddOnMapper {

    private PackageAddOnMapper() {
    }

    static PackageAddOnResponseDto toResponse(PackageAddOn addOn) {
        Room room = addOn.getRoom();
        Vehicle vehicle = addOn.getVehicle();
        return PackageAddOnResponseDto.builder()
                .id(addOn.getId())
                .note(addOn.getNote())
                .room(room == null ? null : AddOnRoomDto.builder()
                        .id(room.getId())
                        .roomType(room.getRoomType())
                        .pricePerNight(room.getPricePerNight())
                        .totalRooms(room.getTotalRooms())
                        .maxOccupancy(room.getMaxOccupancy())
                        .accommodationId(room.getAccommodation().getId())
                        .accommodationName(room.getAccommodation().getName())
                        .build())
                .vehicle(vehicle == null ? null : AddOnVehicleDto.builder()
                        .id(vehicle.getId())
                        .make(vehicle.getMake())
                        .model(vehicle.getModel())
                        .vehicleType(vehicle.getVehicleType())
                        .seatingCapacity(vehicle.getSeatingCapacity())
                        .pricePerDay(vehicle.getPricePerDay())
                        .build())
                .build();
    }

}
