/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package squawker.jdbi

import java.time.Clock
import spock.lang.Shared
import spock.lang.Subject
import squawker.User
import squawker.time.*
import static java.time.Instant.now
import static java.time.ZoneOffset.UTC

class MessageStoreSpec extends BasePersistenceSpec {

  @Subject @Shared MessageStore messageStore
  def kirk = new User("kirk")
  def spock = new User("spock")

  def setupSpec() {
    messageStore = handle.attach(MessageStore)
    messageStore.createMessageTable()
  }

  def cleanupSpec() {
    handle.execute("drop table message if exists")
  }

  def setup() {
    [kirk, spock].each { userStore.insert(it.username) }
  }

  def cleanup() {
    handle.execute("delete from message")
  }

  def "can retrieve a list of messages posted by a user"() {
    given:
    insertMessage(kirk, "@khan KHAAANNN!")
    insertMessage(spock, "Fascinating!")
    insertMessage(spock, "@kirk That is illogical, Captain.")

    when:
    def posts = messageStore.postsBy(spock)

    then:
    with(posts) {
      size() == 2
      postedBy.every { it == spock }
    }
  }

  def "can insert a message"() {
    given:
    def clock = Clock.fixed(now(), UTC)

    when:
    def message = messageStore.insert(spock, "@bones I was merely stating a fact, Doctor.", clock.instant())

    then:
    def iterator = handle.createQuery("""select u.username, m.text, m.posted_at
                                         from message m, user u
                                         where m.posted_by_id = u.id""")
                         .iterator()
    iterator.hasNext()
    with(iterator.next()) {
      text == message.text
      username == message.postedBy.username
      posted_at.time == clock.instant().toEpochMilli()
    }

    and:
    !iterator.hasNext()
  }

  private void insertMessage(User postedBy, String text) {
    handle.createStatement("""insert into message
                              (posted_by_id, text, posted_at)
                              select id, ?, ? from user where username = ?""")
          .bind(0, text)
          .bind(1, now())
          .bind(2, postedBy.username)
          .execute()
  }
}
