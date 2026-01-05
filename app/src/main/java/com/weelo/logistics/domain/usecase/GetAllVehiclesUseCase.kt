package com.weelo.logistics.domain.usecase

import com.weelo.logistics.core.common.Result
import com.weelo.logistics.domain.model.VehicleModel
import com.weelo.logistics.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting all available vehicles
 */
class GetAllVehiclesUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository
) {
    operator fun invoke(): Flow<Result<List<VehicleModel>>> {
        return vehicleRepository.getAllVehicles()
    }
}
