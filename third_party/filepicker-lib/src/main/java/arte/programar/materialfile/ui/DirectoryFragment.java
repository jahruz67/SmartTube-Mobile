package arte.programar.materialfile.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import arte.programar.materialfile.R;
import arte.programar.materialfile.filter.FileFilter;
import arte.programar.materialfile.utils.FileUtils;
import arte.programar.materialfile.widget.EmptyRecyclerView;

public class DirectoryFragment extends Fragment {

    private static final String ARG_FILE = "arg_file_path";
    private static final String ARG_FILTER = "arg_filter";

    private View mEmptyView;
    private File mFile;

    private FileFilter mFilter;

    private EmptyRecyclerView mDirectoryRecyclerView;
    private DirectoryAdapter mDirectoryAdapter;
    private FileClickListener mFileClickListener;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private ExecutorService mListExecutor;
    private Future<?> mListTask;
    private long mLoadGeneration;

    static DirectoryFragment getInstance(
            File file,
            FileFilter filter
    ) {
        final DirectoryFragment instance = new DirectoryFragment();

        final Bundle args = new Bundle();
        args.putSerializable(ARG_FILE, file);
        args.putSerializable(ARG_FILTER, filter);
        instance.setArguments(args);

        return instance;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mFileClickListener = (FileClickListener) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mFileClickListener = null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_directory, container, false);

        mDirectoryRecyclerView = view.findViewById(R.id.directory_recycler_view);
        mEmptyView = view.findViewById(R.id.directory_empty_view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initArgs();
        initFilesList();
    }

    private void initFilesList() {
        mEmptyView.setVisibility(View.GONE);
        mDirectoryRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        long generation = ++mLoadGeneration;
        File directory = mFile;
        FileFilter filter = mFilter;
        mListExecutor = Executors.newSingleThreadExecutor();
        mListTask = mListExecutor.submit(() -> {
            DirectoryAdapter adapter;
            try {
                adapter = new DirectoryAdapter(FileUtils.getFileList(directory, filter));
            } catch (CancellationException e) {
                return;
            } catch (SecurityException e) {
                Log.e("DirectoryFragment", "Cannot read directory", e);
                adapter = new DirectoryAdapter(Collections.emptyList());
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            DirectoryAdapter result = adapter;
            mUiHandler.post(() -> {
                if (generation == mLoadGeneration && mDirectoryRecyclerView != null) {
                    bindFilesList(result);
                }
            });
        });
        mListExecutor.shutdown();
    }

    private void bindFilesList(DirectoryAdapter adapter) {
        mDirectoryAdapter = adapter;
        mDirectoryAdapter.setOnItemClickListener(new ThrottleClickListener() {
            @Override
            void onItemClickThrottled(View view, int position) {
                if (mFileClickListener != null) {
                    mFileClickListener.onFileClicked(mDirectoryAdapter.getModel(position));
                }
            }
        });

        mDirectoryRecyclerView.setAdapter(mDirectoryAdapter);
        mDirectoryRecyclerView.setEmptyView(mEmptyView);
    }

    @Override
    public void onDestroyView() {
        mLoadGeneration++;
        if (mListTask != null) {
            mListTask.cancel(true);
            mListTask = null;
        }
        if (mListExecutor != null) {
            mListExecutor.shutdownNow();
            mListExecutor = null;
        }
        mUiHandler.removeCallbacksAndMessages(null);
        if (mDirectoryRecyclerView != null) {
            mDirectoryRecyclerView.setAdapter(null);
        }
        mDirectoryRecyclerView = null;
        mDirectoryAdapter = null;
        mEmptyView = null;
        super.onDestroyView();
    }

    private void initArgs() {
        final Bundle arguments = requireArguments();

        if (arguments.containsKey(ARG_FILE)) {
            mFile = (File) getArguments().getSerializable(ARG_FILE);
        }

        mFilter = (FileFilter) getArguments().getSerializable(ARG_FILTER);
    }

    interface FileClickListener {
        void onFileClicked(File clickedFile);
    }
}
