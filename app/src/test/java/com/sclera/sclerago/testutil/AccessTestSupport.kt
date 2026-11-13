package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.usecase.access.AccessUseCases
import com.sclera.sclerago.domain.usecase.access.CanDeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.CountPeopleGivenAllPropertiesUseCase
import com.sclera.sclerago.domain.usecase.access.DeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ObserveGroupsUseCase
import com.sclera.sclerago.domain.usecase.access.ObservePeopleUseCase
import com.sclera.sclerago.domain.usecase.access.RemovePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SaveGroupUseCase
import com.sclera.sclerago.domain.usecase.access.SavePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SeedSignedInUserUseCase
import com.sclera.sclerago.domain.usecase.access.ValidateGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ValidatePersonUseCase

/** The people-and-access use cases over [repo], wired as Hilt does. */
fun accessUseCases(
    repo: AccessRepository,
    ids: IdGenerator = SequentialIdGenerator(),
    time: TimeProvider = FakeTimeProvider(),
): AccessUseCases {
    val canDelete = CanDeleteGroupUseCase(repo)
    return AccessUseCases(
        ObserveGroupsUseCase(repo), ObservePeopleUseCase(repo), ValidatePersonUseCase(repo), ValidateGroupUseCase(repo),
        SeedSignedInUserUseCase(repo, ids, time), SavePersonUseCase(repo, ids), RemovePersonUseCase(repo),
        CountPeopleGivenAllPropertiesUseCase(repo), SaveGroupUseCase(repo, ids), canDelete, DeleteGroupUseCase(repo, canDelete),
    )
}
