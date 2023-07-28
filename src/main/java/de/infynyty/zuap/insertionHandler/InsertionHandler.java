package de.infynyty.zuap.insertionHandler;

import de.infynyty.zuap.Zuap;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class InsertionHandler<Insertion extends de.infynyty.zuap.insertion.Insertion> {

    /**
     * Contains all locally saved insertions.
     */
    private final ArrayList<Insertion> currentInsertions = new ArrayList<>();
    /**
     * Used to compare updated insertions with locally saved info. Should be empty before updating local insertions.
     */
    private List<Insertion> updatedInsertions = new ArrayList<>();
    @NotNull
    private final String handlerName;
    @NotNull
    private final InsertionAnnouncer announcer;
    @NotNull
    private final HttpClient httpClient;
    private boolean isInitialized = false;

    /**
     * Update the html data containing all insertions.
     *
     * @return The updated html.
     * @throws IOException
     * @throws InterruptedException
     */
    protected abstract String pullUpdatedData() throws IOException, InterruptedException;

    /**
     * Parses the entire data file, so that all insertions are read into {@link Insertion} objects.
     *
     * @param data The data containing all insertions.
     * @return A list containing all parsed insertions.
     * @throws IllegalStateException If the link to a given insertion cannot be parsed an exception is thrown because
     *                               the links are considered critical information for an {@link Insertion} object.
     */
    protected abstract ArrayList<Insertion> getInsertionsFromData(final String data) throws IllegalStateException;

    /**
     * Updates the currently saved insertions. Online changes to insertions will be mirrored locally in
     * {@link InsertionHandler#currentInsertions}.
     */
    public void updateCurrentInsertions() {
        //clear local list of updated insertions every time this method is called
        updatedInsertions.clear();
        try {
            final String updatedData = pullUpdatedData();
            saveToDisk(updatedData);
            updatedInsertions.addAll(getInsertionsFromData(updatedData));
        } catch (Exception e) {
            Zuap.log(Level.SEVERE, handlerName, "An exception occurred while trying to update the insertions. " + e.getMessage());
            return;
        }
        updatedInsertions = updatedInsertions.stream().filter(insertion -> {
            String canton = insertion.getCanton();
            if (canton == null) {
                Zuap.log(Level.WARNING, handlerName, "Insertion " + insertion.getInsertionURI() + " has no canton.");
                return true;
            }
            if (canton.equals("Zurich")) {
                return true;
            } else {
                Zuap.log(Level.INFO, handlerName, "Insertion in canton " + canton + " was filtered out.");
                return false;
            }
        }).collect(Collectors.toCollection(ArrayList::new));
        if (!isInitialized) {
            addInitialInsertions();
            return;
        }
        addNewInsertions();
        removeDeletedInsertions();
        Zuap.log(
                Level.INFO,
                handlerName,
                "Insertions updated at " + Date.from(Instant.now()) + ", numbers of insertions: " + updatedInsertions.size()
        );
    }

    private Path getHandlerDataPath() {
        return Path.of(handlerName + ".handlerData");
    }

    public void loadFromDisk() throws IOException {
        final String data = Files.readString(getHandlerDataPath());
        currentInsertions.addAll(getInsertionsFromData(data));
        isInitialized = true;
    }

    public void saveToDisk(String data) throws IOException {
        Files.writeString(getHandlerDataPath(), data);
    }

    /**
     * Removes any local insertion that does not exist online anymore.
     */
    private void removeDeletedInsertions() {
        // go through all current insertions and check that they are still in the updated insertions
        // remove them, if that isn't the case
        final boolean wasRemoved = currentInsertions.removeIf(
                currentInsertion -> (!(updatedInsertions.contains(currentInsertion)))
        );
        if (wasRemoved) {
            Zuap.log(Level.INFO, handlerName, "One or more insertions were removed.");
        }
    }

    /**
     * Add every new insertion to {@link InsertionHandler#currentInsertions a local list}.
     */
    private void addNewInsertions() {
        for (final Insertion updatedInsertion : updatedInsertions) {
            if (!(currentInsertions.contains(updatedInsertion))) {
                currentInsertions.add(updatedInsertion);
                announcer.announce(updatedInsertion);
            }
        }
    }

    /**
     * Adds all insertions without checking for duplicates, if there are no insertions saved locally.
     */
    private void addInitialInsertions() {
        isInitialized = true;
        currentInsertions.addAll(updatedInsertions);
        Zuap.log(Level.INFO, handlerName, "Initial download of all insertions complete.");
    }

    public @NotNull String getHandlerName() {
        return handlerName;
    }

    protected @NotNull HttpClient getHttpClient() {
        return httpClient;
    }
}
