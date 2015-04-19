/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package todoapp.integtests.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.CoreMatchers.containsString;
import static todoapp.integtests.assertions.Assertions.assertThat;
import static todoapp.integtests.assertions.BddAssertions.then;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.EventObject;
import java.util.List;

import javax.activation.MimeType;
import javax.inject.Inject;

import org.apache.isis.applib.DomainObjectContainer;
import org.apache.isis.applib.NonRecoverableException;
import org.apache.isis.applib.RecoverableException;
import org.apache.isis.applib.clock.Clock;
import org.apache.isis.applib.fixturescripts.FixtureScripts;
import org.apache.isis.applib.services.clock.ClockService;
import org.apache.isis.applib.services.eventbus.ActionDomainEvent;
import org.apache.isis.applib.services.eventbus.CollectionDomainEvent;
import org.apache.isis.applib.services.eventbus.PropertyDomainEvent;
import org.apache.isis.applib.value.Blob;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import todoapp.dom.app.demoeventsubscriber.DemoBehaviour;
import todoapp.dom.app.demoeventsubscriber.DemoDomainEventSubscriptions;
import todoapp.dom.module.todoitem.ToDoItem;
import todoapp.dom.module.todoitem.ToDoItems;
import todoapp.fixture.scenarios.RecreateToDoItemsForCurrentUser;

import com.google.common.collect.Iterables;

public class ToDoItemIntegTest extends AbstractToDoIntegTest {

    @Inject
    DomainObjectContainer container;
    @Inject
    FixtureScripts fixtureScripts;
    @Inject
    ToDoItems toDoItems;
    @Inject
    DemoDomainEventSubscriptions toDoItemSubscriptions;

    RecreateToDoItemsForCurrentUser fixtureScript;
    ToDoItem toDoItem;

    @Before
    public void setUp() throws Exception {
        fixtureScript = new RecreateToDoItemsForCurrentUser();
        fixtureScripts.runFixtureScript(fixtureScript, null);

        toDoItemSubscriptions.reset();
        final List<ToDoItem> all = toDoItems.notYetComplete();
        toDoItem = wrap(all.get(0));
    }

    public static class Title extends ToDoItemIntegTest {


        @Override
        @Before
        public void setUp() throws Exception {
            super.setUp();

            final List<ToDoItem> notYetComplete = fixtureScript.getNotYetComplete();
            final Iterable<ToDoItem> iter = Iterables.filter(notYetComplete, ToDoItem.Predicates.thoseWithDueByDate());
            toDoItem = wrap(iter.iterator().next());
            assertThat(toDoItem).isNotNull();

            nextTransaction();
        }


        @Test
        public void includesDescription() throws Exception {

            // given
            final String description = toDoItem.getDescription();
            assertThat(container.titleOf(toDoItem)).contains(description);

            // when
            unwrap(toDoItem).setDescription("Foobar");

            // then
            then(container.titleOf(toDoItem)).contains("Foobar");
        }

        @Test
        public void includesDueDateIfAny() throws Exception {

            // given
            final LocalDate dueBy = toDoItem.getDueBy();
            assertThat(container.titleOf(toDoItem)).contains("due by " + dueBy.toString("yyyy-MM-dd"));

            // when
            final LocalDate fiveDaysFromNow = Clock.getTimeAsLocalDate().plusDays(5);
            unwrap(toDoItem).setDueBy(fiveDaysFromNow);

            // then
            then(container.titleOf(toDoItem)).contains("due by " + fiveDaysFromNow.toString("yyyy-MM-dd"));
        }


        @Test
        public void ignoresDueDateIfNone() throws Exception {

            // when
            // (since wrapped, will call clearDueBy)
            toDoItem.setDueBy(null);

            // then
            then(container.titleOf(toDoItem)).doesNotContain("due by");
        }

        @Test
        public void usesWhetherCompleted() throws Exception {

            // given
            assertThat(container.titleOf(toDoItem)).doesNotContain("Completed!");

            // when
            toDoItem.completed();

            // then
            then(container.titleOf(toDoItem)).doesNotContain("due by")
                                             .contains("Completed!");
        }
    }

    public static class Actions {

        public static class Completed extends ToDoItemIntegTest {

            @Test
            public void happyCase() throws Exception {

                // given
                assertThat(toDoItem.isComplete()).isFalse();

                // when
                toDoItem.completed();

                // then
                then(toDoItem).isComplete();
            }

            @Test
            public void cannotCompleteIfAlreadyCompleted() throws Exception {

                // given
                unwrap(toDoItem).setComplete(true);

                // expect
                expectedExceptions.expectMessage("Already completed");

                // when
                toDoItem.completed();

                // and then
                final EventObject ev = toDoItemSubscriptions.mostRecentlyReceivedEvent(EventObject.class);
                then(ev).isNull();
            }


            @Test
            public void cannotSetPropertyDirectly() throws Exception {

                // expect
                expectedExceptions.expectMessage("Always disabled");

                // when
                toDoItem.setComplete(true);

                // and then
                final EventObject ev = toDoItemSubscriptions.mostRecentlyReceivedEvent(EventObject.class);
                then(ev).isNull();
            }

            @Test
            public void subscriberReceivesEvents() throws Exception {

                // given
                toDoItemSubscriptions.reset();
                assertThat(toDoItemSubscriptions.getSubscriberBehaviour()).isEqualTo(DemoBehaviour.ANY_EXECUTE_ACCEPT);
                assertThat(unwrap(toDoItem).isComplete()).isFalse();

                // when
                toDoItem.completed();

                // then
                then(unwrap(toDoItem)).isComplete();

                // and then
                final List<ToDoItem.CompletedEvent> receivedEvents = toDoItemSubscriptions.receivedEvents(ToDoItem.CompletedEvent.class);

                // hide, disable, validate, executing, executed
                // sent to both the general on(ActionInteractionEvent ev)
                // and also the specific on(final ToDoItem.CompletedEvent ev)
                then(receivedEvents).hasSize(5*2);
                final ToDoItem.CompletedEvent ev = receivedEvents.get(0);

                final ToDoItem source = ev.getSource();
                then(source).isEqualTo(unwrap(toDoItem));
                then(ev.getIdentifier().getMemberName()).isEqualTo("completed");
            }

            @Test
            public void subscriberVetoesEventWithRecoverableException() throws Exception {

                // given
                toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_RECOVERABLE_EXCEPTION);

                // then
                expectedExceptions.expect(RecoverableException.class);
                expectedExceptions.expectMessage("Rejecting event (recoverable exception thrown)");

                // when
                toDoItem.completed();
            }

            @Test
            public void subscriberVetoesEventWithNonRecoverableException() throws Exception {

                // given
                toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_NON_RECOVERABLE_EXCEPTION);

                // then
                expectedExceptions.expect(NonRecoverableException.class);
                expectedExceptions.expectMessage("Rejecting event (non-recoverable exception thrown)");

                // when
                toDoItem.completed();
            }

            @Test
            public void subscriberVetoesEventWithAnyOtherException() throws Exception {

                // given
                toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_OTHER_EXCEPTION);

                // then
                expectedExceptions.expect(RuntimeException.class);
                expectedExceptions.expectMessage("Throwing some other exception");

                // when
                toDoItem.completed();
            }
        }


        public static class Duplicate extends ToDoItemIntegTest {

            ToDoItem duplicateToDoItem;

            @Inject
            private ClockService clockService;

            @Test
            public void happyCase() throws Exception {

                // given
                final LocalDate todaysDate = clockService.now();
                toDoItem.setDueBy(todaysDate);
                toDoItem.updateCost(new BigDecimal("123.45"));

                duplicateToDoItem = toDoItem.duplicate(
                        unwrap(toDoItem).default0Duplicate(),
                        unwrap(toDoItem).default1Duplicate(),
                        unwrap(toDoItem).default2Duplicate(),
                        unwrap(toDoItem).default3Duplicate(),
                        new BigDecimal("987.65"));

                // then
                then(duplicateToDoItem).hasDescription(toDoItem.getDescription() + " - Copy")
                                       .hasCategory(toDoItem.getCategory()).hasDueBy(todaysDate)
                                       .hasCost(new BigDecimal("987.65"));
            }
            
        }

        public static class NotYetCompleted extends ToDoItemIntegTest {

            @Test
            public void happyCase() throws Exception {

                // given
                unwrap(toDoItem).setComplete(true);

                // when
                toDoItem.notYetCompleted();

                // then
                then(toDoItem).isNotComplete();
            }

            @Test
            public void cannotUndoIfNotYetCompleted() throws Exception {

                // given
                assertThat(toDoItem).isNotComplete();

                // when, then should fail
                expectedExceptions.expectMessage("Not yet completed");
                toDoItem.notYetCompleted();
            }

            @Test
            public void subscriberReceivesEvent() throws Exception {

                // given
                assertThat(toDoItemSubscriptions.getSubscriberBehaviour()).isEqualTo(DemoBehaviour.ANY_EXECUTE_ACCEPT);
                unwrap(toDoItem).setComplete(true);

                // when
                toDoItem.notYetCompleted();

                // then
                then(unwrap(toDoItem)).isNotComplete();

                // and then
                final ActionDomainEvent<ToDoItem> ev = toDoItemSubscriptions.mostRecentlyReceivedEvent(ActionDomainEvent.class);
                assertThat(ev).isNotNull();

                final ToDoItem source = ev.getSource();
                assertThat(source).isEqualTo(unwrap(toDoItem));
                assertThat(ev.getIdentifier().getMemberName()).isEqualTo("notYetCompleted");
            }
        }
    }

    public static class Collections {

        public static class Dependencies {
            public static class Add extends ToDoItemIntegTest {

                private ToDoItem otherToDoItem;

                @Override
                @Before
                public void setUp() throws Exception {
                    super.setUp();
                    final List<ToDoItem> items = wrap(toDoItems).notYetComplete();
                    otherToDoItem = wrap(items.get(1));
                }

                @After
                public void tearDown() throws Exception {
                    unwrap(toDoItem).getDependencies().clear();
                }

                @Test
                public void happyCase() throws Exception {

                    // given
                    assertThat(toDoItem.getDependencies()).hasSize(0);

                    // when
                    toDoItem.add(otherToDoItem);

                    // then
                    assertThat(toDoItem).hasOnlyDependencies(unwrap(otherToDoItem));
                }


                @Test
                public void cannotDependOnSelf() throws Exception {

                    // then
                    expectedExceptions.expectMessage("Can't set up a dependency to self");

                    // when
                    toDoItem.add(toDoItem);
                }

                @Test
                public void cannotAddIfComplete() throws Exception {

                    // given
                    unwrap(toDoItem).setComplete(true);

                    // then
                    expectedExceptions.expectMessage("Cannot add dependencies for items that are complete");

                    // when
                    toDoItem.add(otherToDoItem);
                }


                @Test
                public void subscriberReceivesEvent() throws Exception {

                    // given
                    toDoItemSubscriptions.reset();

                    // when
                    toDoItem.add(otherToDoItem);

                    // then received events
                    @SuppressWarnings("unchecked")
                    final List<EventObject> receivedEvents = toDoItemSubscriptions.receivedEvents();

                    assertThat(receivedEvents).hasSize(7);
                    assertThat(receivedEvents.get(0)).isInstanceOf(ActionDomainEvent.class); // ToDoItem#add() executed
                    assertThat(receivedEvents.get(1)).isInstanceOf(CollectionDomainEvent.class); // ToDoItem#dependencies add, executed
                    assertThat(receivedEvents.get(2)).isInstanceOf(CollectionDomainEvent.class); // ToDoItem#dependencies add, executing
                    assertThat(receivedEvents.get(3)).isInstanceOf(ActionDomainEvent.class); // ToDoItem#add executing
                    assertThat(receivedEvents.get(4)).isInstanceOf(ActionDomainEvent.class); // ToDoItem#add validate
                    assertThat(receivedEvents.get(5)).isInstanceOf(ActionDomainEvent.class); // ToDoItem#add disable
                    assertThat(receivedEvents.get(6)).isInstanceOf(ActionDomainEvent.class); // ToDoItem#add hide

                    // inspect the collection interaction (posted programmatically in ToDoItem#add)
                    final CollectionDomainEvent<ToDoItem,ToDoItem> ciEv = toDoItemSubscriptions.mostRecentlyReceivedEvent(CollectionDomainEvent.class);
                    assertThat(ciEv).isNotNull();

                    assertThat(ciEv.getSource()).isEqualTo(unwrap(toDoItem));
                    assertThat(ciEv.getIdentifier().getMemberName()).isEqualTo("dependencies");
                    assertThat(ciEv.getOf()).isEqualTo(CollectionDomainEvent.Of.ADD_TO);
                    assertThat(ciEv.getValue()).isEqualTo(unwrap(otherToDoItem));

                    // inspect the action interaction (posted declaratively by framework)
                    final ActionDomainEvent<ToDoItem> aiEv = toDoItemSubscriptions.mostRecentlyReceivedEvent(ActionDomainEvent.class);
                    assertThat(aiEv).isNotNull();

                    assertThat(aiEv.getSource()).isEqualTo(unwrap(toDoItem));
                    assertThat(aiEv.getIdentifier().getMemberName()).isEqualTo("add");
                    assertThat(aiEv.getArguments()).containsExactly(unwrap((Object)otherToDoItem));
                    assertThat(aiEv.getCommand()).isNotNull();
                }

                @Test
                public void subscriberVetoesEventWithRecoverableException() throws Exception {

                    // given
                    toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_RECOVERABLE_EXCEPTION);

                    // then
                    expectedExceptions.expect(RecoverableException.class);

                    // when
                    toDoItem.add(otherToDoItem);
                }

                @Test
                public void subscriberVetoesEventWithNonRecoverableException() throws Exception {

                    // given
                    toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_NON_RECOVERABLE_EXCEPTION);

                    // then
                    expectedExceptions.expect(NonRecoverableException.class);

                    // when
                    toDoItem.add(otherToDoItem);
                }

                @Test
                public void subscriberVetoesEventWithAnyOtherException() throws Exception {

                    // given
                    toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_OTHER_EXCEPTION);

                    // then
                    expectedExceptions.expect(RuntimeException.class);

                    // when
                    toDoItem.add(otherToDoItem);
                }
            }
            public static class Remove extends ToDoItemIntegTest {

                private ToDoItem otherToDoItem;
                private ToDoItem yetAnotherToDoItem;

                @Override
                @Before
                public void setUp() throws Exception {
                    super.setUp();
                    final List<ToDoItem> items = wrap(toDoItems).notYetComplete();
                    otherToDoItem = wrap(items.get(1));
                    yetAnotherToDoItem = wrap(items.get(2));

                    toDoItem.add(otherToDoItem);
                }

                @After
                public void tearDown() throws Exception {
                    unwrap(toDoItem).getDependencies().clear();
                }

                @Test
                public void happyCase() throws Exception {

                    // given
                    assertThat(toDoItem.getDependencies()).hasSize(1);

                    // when
                    toDoItem.remove(otherToDoItem);

                    // then
                    then(toDoItem).hasNoDependencies();
                }


                @Test
                public void cannotRemoveItemIfNotADependency() throws Exception {

                    // then
                    expectedExceptions.expectMessage("Not a dependency");

                    // when
                    toDoItem.remove(yetAnotherToDoItem);
                }

                @Test
                public void cannotRemoveDependencyIfComplete() throws Exception {

                    // given
                    unwrap(toDoItem).setComplete(true);

                    // then
                    expectedExceptions.expectMessage("Cannot remove dependencies for items that are complete");

                    // when
                    toDoItem.remove(otherToDoItem);
                }

                @Test
                public void subscriberVetoesEventWithRecoverableException() throws Exception {

                    // given
                    toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_RECOVERABLE_EXCEPTION);

                    // then
                    expectedExceptions.expect(RecoverableException.class);

                    // when
                    toDoItem.remove(otherToDoItem);
                }

                @Test
                public void subscriberVetoesEventWithNonRecoverableException() throws Exception {

                    // given
                    toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_NON_RECOVERABLE_EXCEPTION);

                    // then
                    expectedExceptions.expect(NonRecoverableException.class);

                    // when
                    toDoItem.remove(otherToDoItem);
                }

                @Test
                public void subscriberVetoesEventWithAnyOtherException() throws Exception {

                    // given
                    toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_OTHER_EXCEPTION);

                    // then
                    expectedExceptions.expect(RuntimeException.class);

                    // when
                    toDoItem.remove(otherToDoItem);
                }
            }
        }

    }

    public static class Properties {

        public static class Attachment extends ToDoItemIntegTest {

            @Test
            public void happyCase() throws Exception {

                final byte[] bytes = "{\"foo\": \"bar\"}".getBytes(Charset.forName("UTF-8"));
                final Blob newAttachment = new Blob("myfile.json", new MimeType("application/json"), bytes);

                // when
                toDoItem.setAttachment(newAttachment);

                // then
                then(toDoItem).hasAttachment(newAttachment);
            }

            @Test
            public void canBeNull() throws Exception {

                // when
                toDoItem.setAttachment(null);

                // then
                then(toDoItem).hasAttachment(null);
            }
        }

        public static class Category extends ToDoItemIntegTest {

            @Test
            public void cannotModify() throws Exception {

                // when, then
                expectedExceptions.expectMessage(containsString("Reason: Use action to update both category and subcategory."));
                toDoItem.setCategory(todoapp.dom.module.categories.Category.PROFESSIONAL);
            }
        }

        public static class Cost extends ToDoItemIntegTest {

            private BigDecimal cost;

            @Override
            @Before
            public void setUp() throws Exception {
                super.setUp();
                cost = toDoItem.getCost();
            }

            @Test
            public void happyCaseUsingProperty() throws Exception {

                final BigDecimal newCost = new BigDecimal("123.45");

                // when
                toDoItem.updateCost(newCost);

                // then
                then(toDoItem).hasCost(newCost);
            }

            @Test
            public void happyCaseUsingAction() throws Exception {

                final BigDecimal newCost = new BigDecimal("123.45");

                // when
                toDoItem.updateCost(newCost);

                // then
                then(toDoItem).hasCost(newCost);
            }

            @Test
            public void canBeNull() throws Exception {

                // when
                toDoItem.updateCost(null);

                // then
                then(toDoItem).hasCost(null);

            }

            @Test
            public void defaultForAction() throws Exception {

                // then
                then(unwrap(toDoItem)).hasCost(cost);
            }

        }

        public static class Description extends ToDoItemIntegTest {

            @Test
            public void happyCase() throws Exception {

                // given
                final String description = toDoItem.getDescription();

                // when
                toDoItem.setDescription(description + " foobar");

                // then
                then(toDoItem).hasDescription(description + " foobar");
            }


            @Test
            public void failsRegex() throws Exception {

                // when
                expectedExceptions.expectMessage("Doesn't match pattern");
                toDoItem.setDescription("exclamation marks are not allowed!!!");
            }

            @Test
            public void cannotBeNull() throws Exception {

                // when, then
                expectedExceptions.expectMessage("'Description' is mandatory");
                toDoItem.setDescription(null);
            }

            @Test
            public void cannotUseModify() throws Exception {

                expectedExceptions.expectMessage("Cannot invoke supporting method for 'Description'; use only property accessor/mutator");

                // given
                final String description = toDoItem.getDescription();

                // when
                toDoItem.modifyDescription(description + " foobar!");

                // then
                then(toDoItem).hasDescription(description);
            }

            @Test
            public void cannotUseClear() throws Exception {

                expectedExceptions.expectMessage("Cannot invoke supporting method for 'Description'; use only property accessor/mutator");

                // given
                final String description = toDoItem.getDescription();

                // when
                toDoItem.clearDescription();

                // then
                then(toDoItem).hasDescription(description);
            }


            @Test
            public void onlyJustShortEnough() throws Exception {

                // when, then
                toDoItem.setDescription(characters(100));
            }

            @Test
            public void tooLong() throws Exception {

                // then
                expectedExceptions.expectMessage("The value proposed exceeds the maximum length of 100");

                // when
                toDoItem.setDescription(characters(101));
            }


            @Test
            public void subscriberReceivesEvent() throws Exception {

                // given
                assertThat(toDoItemSubscriptions.getSubscriberBehaviour()).isEqualTo(DemoBehaviour.ANY_EXECUTE_ACCEPT);
                final String description = toDoItem.getDescription();

                // when
                toDoItem.setDescription(description + " foobar");

                // then published and received
                @SuppressWarnings("unchecked")
                final PropertyDomainEvent<ToDoItem,String> ev = toDoItemSubscriptions.mostRecentlyReceivedEvent(PropertyDomainEvent.class);
                then(ev).isNotNull();

                final ToDoItem source = ev.getSource();
                then(source).isEqualTo(unwrap(toDoItem));
                then(ev.getIdentifier().getMemberName()).isEqualTo("description");
                then(ev).hasOldValue(description)
                        .hasNewValue(description + " foobar");
            }

            @Test
            public void subscriberVetoesEventWithRecoverableException() throws Exception {

                // given
                toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_RECOVERABLE_EXCEPTION);

                // then
                expectedExceptions.expect(RecoverableException.class);

                // when
                toDoItem.setDescription("Buy bread and butter");
            }


            @Test
            public void subscriberVetoesEventWithNonRecoverableException() throws Exception {

                // given
                toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_NON_RECOVERABLE_EXCEPTION);

                // then
                expectedExceptions.expect(NonRecoverableException.class);

                // when
                toDoItem.setDescription("Buy bread and butter");
            }


            @Test
            public void subscriberVetoesEventWithAnyOtherException() throws Exception {

                // given
                toDoItemSubscriptions.subscriberBehaviour(DemoBehaviour.ANY_EXECUTE_VETO_WITH_OTHER_EXCEPTION);

                // then
                expectedExceptions.expect(RuntimeException.class);

                // when
                toDoItem.setDescription("Buy bread and butter");
            }


            private static String characters(final int n) {
                final StringBuffer buf = new StringBuffer();
                for(int i=0; i<n; i++) {
                    buf.append("a");
                }
                return buf.toString();
            }
        }

        public static class DueBy extends ToDoItemIntegTest {

            @Inject
            private ClockService clockService;

            @Test
            public void happyCase() throws Exception {

                // when
                final LocalDate fiveDaysFromNow = clockService.now().plusDays(5);
                toDoItem.setDueBy(fiveDaysFromNow);

                // then
                then(toDoItem).hasDueBy(fiveDaysFromNow);
            }


            @Test
            public void canBeNull() throws Exception {

                // when
                toDoItem.setDueBy(null);

                // then
                then(toDoItem).hasDueBy(null);
            }

            @Test
            public void canBeUpToSixDaysInPast() throws Exception {

                final LocalDate nowAsLocalDate = clockService.now();
                final LocalDate sixDaysAgo = nowAsLocalDate.plusDays(-5);

                // when
                toDoItem.setDueBy(sixDaysAgo);

                // then
                then(toDoItem).hasDueBy(sixDaysAgo);
            }


            @Test
            public void cannotBeMoreThanSixDaysInPast() throws Exception {

                final LocalDate sevenDaysAgo = Clock.getTimeAsLocalDate().plusDays(-7);

                // when, then
                expectedExceptions.expectMessage("Due by date cannot be more than one week old");
                toDoItem.setDueBy(sevenDaysAgo);
            }
        }

        public static class Notes extends ToDoItemIntegTest {

            @Test
            public void happyCase() throws Exception {

                final String newNotes = "Lorem ipsum yada yada";

                // when
                toDoItem.setNotes(newNotes);

                // then
                assertThat(toDoItem).hasNotes(newNotes);
            }

            @Test
            public void canBeNull() throws Exception {

                // when
                toDoItem.setNotes(null);

                // then
                then(toDoItem).hasNotes(null);
            }

            @Test
            public void suscriberReceivedDefaultEvent() throws Exception {

                final String newNotes = "Lorem ipsum yada yada";

                // when
                toDoItem.setNotes(newNotes);

                // then
                assertThat(unwrap(toDoItem)).hasNotes(newNotes);

                // and then receive the default event.
                @SuppressWarnings("unchecked")
                final PropertyDomainEvent.Default ev = toDoItemSubscriptions.mostRecentlyReceivedEvent(PropertyDomainEvent.Default.class);
                assertThat(ev).isNotNull();

                assertThat(ev.getSource()).isEqualTo(unwrap(toDoItem));
                assertThat(ev).hasNewValue(newNotes);
            }


        }

        public static class OwnedBy extends ToDoItemIntegTest {

            @Test
            public void cannotModify() throws Exception {

                // when, then
                expectedExceptions.expectMessage("Reason: Always disabled. Identifier: todoapp.dom.module.todoitem.ToDoItem#atPath()");
                toDoItem.setAtPath("other");
            }


        }

        public static class Subcategory extends ToDoItemIntegTest {

            @Test
            public void cannotModify() throws Exception {

                // when, then
                expectedExceptions.expectMessage(containsString("Reason: Use action to update both category and subcategory."));
                toDoItem.setSubcategory(todoapp.dom.module.categories.Subcategory.CHORES);
            }
        }

    }




}