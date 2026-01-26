# Vitruvius Brake Case Study 
- based on the Methodologist Template (see [here](https://github.com/vitruv-tools/Methodologist-Template/tree/main))

## Getting Started

The project comes with a maven wrapper, so you can run it without installing Maven.
To build the project you can run the following command:

```bash
./mvnw clean verify
```

Verify that all tests are passing. The tests are located in the `vsum` folder.
Now you can start to modify the project to your needs. Or jump to the [Tutorial](#tutorial) section to get a quick start. First we will explain what tests are run and what they are testing.


## Documentation of the Case Study

In the wiki, you can find the documentation of the different ecore models and the semantic overlaps defined between the different metamodels.

## General Project Structure


Model
-----
This folder contains the model in the ecore format. When you do not use eclipse, please provide a genmodel of your ecore model so that code can be generated. 

Consistency
-----------
This folder contains the consistency specifications, like reactions.

ViewType
--------
This folder contains the definition of the view types. These are necessary to create views of the vsum. 

Vsum
----
This folder contains the VSUM

Useful Links
------------
Details about the build process and configurations can be found in the readmes of the relevant projects.
* https://github.com/vitruv-tools/Maven-Build-Parent/blob/main/readme.md
* https://github.com/vitruv-tools/EMF-Template/blob/main/readme.md



