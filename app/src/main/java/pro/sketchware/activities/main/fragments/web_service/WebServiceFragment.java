package pro.sketchware.activities.main.fragments.web_service;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.transition.MaterialFadeThrough;

import pro.sketchware.activities.main.fragments.web_service.adapters.WebServiceAdapter;
import pro.sketchware.databinding.FragmentWebServiceBinding;
import pro.sketchware.utility.UI;

public class WebServiceFragment extends Fragment {
    private FragmentWebServiceBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentWebServiceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        
        UI.addSystemWindowInsetToPadding(binding.textWebService, true, false, true, false);
        UI.addSystemWindowInsetToPadding(binding.webServiceRecyclerView, true, false, true, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // avoid memory leaks
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        binding.webServiceRecyclerView.setLayoutManager(layoutManager);
        
        WebServiceAdapter adapter = new WebServiceAdapter(getActivity());
        binding.webServiceRecyclerView.setAdapter(adapter);
    }
}


