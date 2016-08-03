---

__This repository is no longer maintained. Issue reports and pull requests will not be attended.__

---

**Introduction**

The android-notification-utils is a set of tools that solve a very common data distribution problem known as the producer-consumer model. The basic question is: how to distribute some data from it's source(the producer) to the components interested in it(the consumers). This is solved commonly in Java using the Listener pattern, where the consumers register directly by the producer to get the data. In means of Object Oriented Programing, this has some major drawbacks:

The consumers must have direct access to the producer -> tight coupling
The amount of producers of a type of data is limited to one -> not flexible
Each producer must implement a common code pattern -> code duplication
All this can be removed using the Notification pattern. In this case the consumers register at a central entity, the NotificationCenter, the interest in specific types of data. The producers one the other hand deliver data to the NotificationCenter, which then handles it's distribution. We have:

Consumers do only need to know about the data type, and the NotificationCenter? -> loose coupling
Multiple producers, multiple consumers allowed -> flexible
Producer side code delivery code reduced to a single NotificationCenter method call -> no code duplication.

**Using the NotificationCenter**

The check list for using NotificationCanter is as follows:

Create one or many instances of NotificationCenter - this could be a single instance for application-wide use, or many more task orientated instances
Some notification types - this will be a classes implementing the Notification interface. Instances of them will hold the to-be-delivered data.
Some producers - when data is ready they only need to construct a notification instance for it, and deliver it to the NotificationCenter using the emitNotification method
Some consumers - this will be classes implementing the NotificationListener generic interface for a particular notification type. This can be ofcourse inner classes of the actual consumer. The registration is achieved using the registerListener method of NotificationCenter.
And that's it! Some Java Generics magic will handle the typesafety and proper delivery of notifications from there source to there destination.
