package com.tourlk.enums;

/**
 * What a {@code Payment} is paying for. {@code Payment.payableId} is the
 * id of the Booking, RoomReservation or VehicleHire, resolved
 * polymorphically via this discriminator rather than several
 * near-duplicate Payment classes.
 */
public enum PayableType {
    BOOKING,
    ROOM_RESERVATION,
    VEHICLE_HIRE
}
