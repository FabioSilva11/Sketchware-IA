package com.besome.sketch.design;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

import pro.sketchware.R;
import pro.sketchware.activities.resourceseditor.components.utils.StringsEditorManager;
import pro.sketchware.databinding.PalletCustomviewBinding;
import pro.sketchware.databinding.ResourcesEditorFragmentBinding;
import pro.sketchware.databinding.ViewStringEditorAddBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.XmlUtil;

public class StringsTabFragment extends Fragment {

    private ResourcesEditorFragmentBinding binding;

    // Main list (public alias for compatibility with callers that expect `listmap`)
    private final ArrayList<HashMap<String, Object>> stringsList = new ArrayList<>();
    public final ArrayList<HashMap<String, Object>> listmap = stringsList;

    // notes: we keep a key-based map for runtime stability, and an index-based map only when saving/loading
    private final HashMap<Integer, String> notesIndexMap = new HashMap<>(); // used temporarily while reading
    private final HashMap<String, String> notesByKey = new HashMap<>(); // stable map keyed by string key

    public StringsEditorManager stringsEditorManager = new StringsEditorManager();

    // Exposed so Activity can check/save as needed (like ResourcesEditorActivity does)
    public boolean hasUnsavedChanges;
    public String filePath;

    private StringsListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ResourcesEditorFragmentBinding.inflate(inflater, container, false);

        // Make sure manager knows project id
        stringsEditorManager.sc_id = DesignActivity.sc_id;

        // Setup adapter first so updateStringsList can just notify it
        adapter = new StringsListAdapter(stringsList);
        binding.recyclerView.setAdapter(adapter);

        // default path
        String baseDir = a.a.a.wq.b(DesignActivity.sc_id) + "/files/resource/values/";
        filePath = baseDir + "strings.xml";

        // initial load
        updateStringsList(filePath, 0, false);

        binding.fabAddString.setOnClickListener(v -> showAddStringDialog());
        return binding.getRoot();
    }

    /**
     * Backwards-compatible refresh
     */
    public void refreshList() {
        updateStringsList(filePath, 0, false);
    }

    /**
     * Public update method with merge modes (same semantics as ResourcesEditorActivity expects)
     *
     * @param path      file path to load (strings.xml)
     * @param mergeMode 0 = replace, 1 = merge & skip existing, 2 = merge & replace existing
     * @param isImport  true when importing from another variant
     */
    public void updateStringsList(String path, int mergeMode, boolean isImport) {
        this.filePath = path;
        ArrayList<HashMap<String, Object>> imported = new ArrayList<>();
        stringsEditorManager.convertXmlStringsToListMap(FileUtil.readFileIfExist(filePath), imported);

        // build imported notes keyed by key (manager provides notes by index)
        HashMap<String, String> importedNotesByKey = new HashMap<>();
        notesIndexMap.clear();
        notesIndexMap.putAll(stringsEditorManager.notesMap);
        for (int i = 0; i < imported.size(); i++) {
            HashMap<String, Object> item = imported.get(i);
            String key = Objects.toString(item.get("key"), "");
            if (!key.isEmpty() && notesIndexMap.containsKey(i)) {
                importedNotesByKey.put(key, notesIndexMap.get(i));
            }
        }

        if (!isImport) {
            // normal load (replace)
            stringsList.clear();
            stringsList.addAll(imported);
            notesByKey.clear();
            notesByKey.putAll(importedNotesByKey);
        } else {
            // import/merge flow
            switch (mergeMode) {
                case 0: // replace
                    stringsList.clear();
                    stringsList.addAll(imported);
                    notesByKey.clear();
                    notesByKey.putAll(importedNotesByKey);
                    break;
                case 1: // merge & skip existing
                    HashSet<String> existingKeys = new HashSet<>();
                    for (HashMap<String, Object> m : stringsList) {
                        existingKeys.add(Objects.toString(m.get("key"), ""));
                    }
                    for (HashMap<String, Object> m : imported) {
                        String k = Objects.toString(m.get("key"), "");
                        if (!existingKeys.contains(k)) {
                            stringsList.add(m);
                        }
                    }
                    // merge notes but keep existing notes untouched
                    for (String k : importedNotesByKey.keySet()) {
                        if (!notesByKey.containsKey(k)) {
                            notesByKey.put(k, importedNotesByKey.get(k));
                        }
                    }
                    break;
                case 2: // merge & replace existing
                    HashMap<String, Integer> indexByKey = new HashMap<>();
                    for (int i = 0; i < stringsList.size(); i++) {
                        indexByKey.put(Objects.toString(stringsList.get(i).get("key"), ""), i);
                    }
                    for (HashMap<String, Object> m : imported) {
                        String k = Objects.toString(m.get("key"), "");
                        if (indexByKey.containsKey(k)) {
                            int pos = indexByKey.get(k);
                            // replace text (keep same map object)
                            stringsList.get(pos).put("text", m.get("text"));
                        } else {
                            stringsList.add(m);
                        }
                    }
                    // imported notes overwrite existing for replaced keys
                    notesByKey.putAll(importedNotesByKey);
                    break;
                default:
                    // unknown mode — fallback to replace
                    stringsList.clear();
                    stringsList.addAll(imported);
                    notesByKey.clear();
                    notesByKey.putAll(importedNotesByKey);
            }
        }

        adapter.notifyDataSetChanged();
        updateNoContentLayout();

        // after load from disk, there's no unsaved change (until user edits)
        hasUnsavedChanges = false;
    }

    private void updateNoContentLayout() {
        if (stringsList.isEmpty()) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.noContentTitle.setText(getString(R.string.resource_manager_no_list_title, "Strings"));
            binding.noContentBody.setText(getString(R.string.resource_manager_no_list_body, "string"));
        } else {
            binding.noContentLayout.setVisibility(View.GONE);
        }
    }

    private void showAddStringDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireActivity());
        ViewStringEditorAddBinding dialogBinding = ViewStringEditorAddBinding.inflate(getLayoutInflater());
        dialog.setTitle("Create new string");
        dialog.setPositiveButton("Create", (d, which) -> {
            String key = Objects.requireNonNull(dialogBinding.stringKeyInput.getText()).toString().trim();
            String value = Objects.requireNonNull(dialogBinding.stringValueInput.getText()).toString();
            String header = Objects.requireNonNull(dialogBinding.stringHeaderInput.getText()).toString().trim();

            if (key.isEmpty() || value.isEmpty()) {
                SketchwareUtil.toastError("Please fill in all fields");
                return;
            }

            if (isDuplicateKey(key, null)) {
                SketchwareUtil.toastError("\"" + key + "\" is already exist");
                return;
            }

            addString(key, value, header);
            updateNoContentLayout();
        });
        dialog.setNegativeButton(getString(R.string.cancel), null);
        dialog.setView(dialogBinding.getRoot());
        dialog.show();
    }

    private void addString(String key, String text, String note) {
        hasUnsavedChanges = true;
        HashMap<String, Object> map = new HashMap<>();
        map.put("key", key);
        map.put("text", text);
        stringsList.add(map);
        int position = stringsList.size() - 1;
        if (!note.isEmpty()) {
            notesByKey.put(key, note);
        }
        adapter.notifyItemInserted(position);

        // Immediately save so changes persist across tab switches
        saveStringsFile();
    }

    private void editString(int position) {
        if (position < 0 || position >= stringsList.size()) return;
        HashMap<String, Object> currentItem = stringsList.get(position);
        String oldKey = Objects.toString(currentItem.get("key"), "");

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireActivity());
        ViewStringEditorAddBinding dialogBinding = ViewStringEditorAddBinding.inflate(getLayoutInflater());

        dialogBinding.stringKeyInput.setText(oldKey);
        dialogBinding.stringValueInput.setText(Objects.toString(currentItem.get("text"), ""));
        dialogBinding.stringHeaderInput.setText(notesByKey.getOrDefault(oldKey, ""));

        if ("app_name".equals(oldKey)) {
            dialogBinding.stringKeyInput.setEnabled(false);
        }

        dialog.setTitle("Edit string");
        dialog.setPositiveButton("Save", (d, which) -> {
            String keyInput = Objects.requireNonNull(dialogBinding.stringKeyInput.getText()).toString().trim();
            String valueInput = Objects.requireNonNull(dialogBinding.stringValueInput.getText()).toString();
            if (keyInput.isEmpty() || valueInput.isEmpty()) {
                SketchwareUtil.toastError("Please fill in all fields");
                return;
            }

            // check duplicate among other items
            if (!oldKey.equals(keyInput) && isDuplicateKey(keyInput, position)) {
                SketchwareUtil.toastError("\"" + keyInput + "\" is already exist");
                return;
            }

            // apply changes
            currentItem.put("key", keyInput);
            currentItem.put("text", valueInput);

            String note = Objects.requireNonNull(dialogBinding.stringHeaderInput.getText()).toString().trim();

            // if key changed, transfer or remove old note as appropriate
            if (!oldKey.equals(keyInput)) {
                // move note from oldKey to new key if user didn't supply a new note
                String existingOldNote = notesByKey.remove(oldKey);
                if (note.isEmpty()) {
                    // If user left note empty, remove any old note (we already removed)
                } else {
                    notesByKey.put(keyInput, note);
                }

                // if there was an old note and user didn't input a new one, keep old note under new key
                if (!notesByKey.containsKey(keyInput) && existingOldNote != null && note.isEmpty()) {
                    notesByKey.put(keyInput, existingOldNote);
                }
            } else {
                // key unchanged
                if (note.isEmpty()) {
                    notesByKey.remove(keyInput);
                } else {
                    notesByKey.put(keyInput, note);
                }
            }

            adapter.notifyItemChanged(position);
            hasUnsavedChanges = true;

            // immediate save
            saveStringsFile();
        });

        if (!Objects.equals(currentItem.get("key"), "app_name")) {
            dialog.setNeutralButton(getString(R.string.common_word_delete), (d, which) -> {
                // delete item and its note
                stringsList.remove(position);
                notesByKey.remove(oldKey);
                adapter.notifyItemRemoved(position);
                updateNoContentLayout();
                hasUnsavedChanges = true;

                // immediate save after delete
                saveStringsFile();
            });
        }
        dialog.setNegativeButton(getString(R.string.cancel), null);
        dialog.setView(dialogBinding.getRoot());
        dialog.show();
    }

    /**
     * Save current stringsList -> filePath
     * We convert notesByKey -> index keyed map according to current list order before saving.
     */
    public void saveStringsFile() {
        if (!hasUnsavedChanges) return;

        HashMap<Integer, String> indexNotesMap = new HashMap<>();
        for (int i = 0; i < stringsList.size(); i++) {
            String key = Objects.toString(stringsList.get(i).get("key"), "");
            if (notesByKey.containsKey(key)) {
                indexNotesMap.put(i, notesByKey.get(key));
            }
        }

        XmlUtil.saveXml(filePath, stringsEditorManager.convertListMapToXmlStrings(stringsList, indexNotesMap));
        hasUnsavedChanges = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveStringsFile();
    }

    /**
     * Utility: check duplicate key excluding optional index
     */
    private boolean isDuplicateKey(String key, Integer exceptPosition) {
        for (int i = 0; i < stringsList.size(); i++) {
            if (exceptPosition != null && i == exceptPosition) continue;
            String k = Objects.toString(stringsList.get(i).get("key"), "");
            if (k.equals(key)) return true;
        }
        return false;
    }

    private class StringsListAdapter extends RecyclerView.Adapter<StringsListAdapter.ViewHolder> {
        private ArrayList<HashMap<String, Object>> data;

        public StringsListAdapter(ArrayList<HashMap<String, Object>> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PalletCustomviewBinding itemBinding = PalletCustomviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HashMap<String, Object> item = data.get(position);
            String key = Objects.toString(item.get("key"), "");
            String text = Objects.toString(item.get("text"), "");
            holder.binding.title.setHint(key);
            holder.binding.sub.setText(text);
            String note = notesByKey.get(key);
            if (note != null) {
                holder.binding.tvTitle.setText(note);
                holder.binding.tvTitle.setVisibility(View.VISIBLE);
            } else {
                holder.binding.tvTitle.setVisibility(View.GONE);
            }
            holder.binding.backgroundCard.setOnClickListener(v -> editString(holder.getAbsoluteAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            PalletCustomviewBinding binding;

            ViewHolder(PalletCustomviewBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}


