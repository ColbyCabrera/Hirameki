// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anim

import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ActivityTransitionAnimationTest {
    @ParameterizedTest
    @EnumSource(
        value = Direction::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["START", "END", "UP", "DOWN", "RIGHT", "LEFT"],
    )
    fun getInverseTransition_returns_same_input_for_not_directional_params(direction: Direction) {
        assertThat(getInverseTransition(direction), equalTo(direction))
    }

    @ParameterizedTest
    @MethodSource("getInverseTransition_returns_inverse_direction_args")
    fun getInverseTransition_returns_inverse_direction(
        first: Direction,
        second: Direction,
    ) {
        assertThat(getInverseTransition(first), equalTo(second))
        assertThat(getInverseTransition(second), equalTo(first))
    }

    companion object {
        @JvmStatic // used in @MethodSource
        fun getInverseTransition_returns_inverse_direction_args(): Stream<Arguments> =
            Stream.of(
                Arguments.of(Direction.START, Direction.END),
                Arguments.of(Direction.UP, Direction.DOWN),
                Arguments.of(Direction.RIGHT, Direction.LEFT),
            )
    }
}
